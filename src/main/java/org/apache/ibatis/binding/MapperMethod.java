/**
 *    Copyright 2009-2019 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.binding;

import org.apache.ibatis.annotations.Flush;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.reflection.TypeParameterResolver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSession;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Mapper 方法; Mapper 接口每定义一个方法，对应一个 invokerMehtod 对象
 * @author Clinton Begin
 * @author Eduardo Macarron
 * @author Lasse Voss
 * @author Kazuki Shimizu
 */
public class MapperMethod {

  /** sqlCommand 对象 */
  private final SqlCommand command;
  /** 方法签名 */
  private final MethodSignature method;

  public MapperMethod(Class<?> mapperInterface, Method method, Configuration config) {
    this.command = new SqlCommand(config, mapperInterface, method);
    this.method = new MethodSignature(config, mapperInterface, method);
  }


  public Object execute(SqlSession sqlSession, Object[] args) {
    Object result;
    switch (command.getType()) {
      case INSERT: {
        // 获得参数列表
        Object param = method.convertArgsToSqlCommandParam(args);
        // 执行sql, 并转换返回值
        result = rowCountResult(sqlSession.insert(command.getName(), param));
        break;
      }
      case UPDATE: {
        Object param = method.convertArgsToSqlCommandParam(args);
        result = rowCountResult(sqlSession.update(command.getName(), param));
        break;
      }
      case DELETE: {
        Object param = method.convertArgsToSqlCommandParam(args);
        result = rowCountResult(sqlSession.delete(command.getName(), param));
        break;
      }
      case SELECT:
        // 方法返回 void && 参数列表含有 ResultHandler
        if (method.returnsVoid() && method.hasResultHandler()) {
          //
          executeWithResultHandler(sqlSession, args);
          result = null;
        }
        // 方法返回 集合或者数组
        else if (method.returnsMany()) {
          result = executeForMany(sqlSession, args);
        }
        // 方法返回 Map
        else if (method.returnsMap()) {
          result = executeForMap(sqlSession, args);
        }
        // 如果返回 Cursor
        else if (method.returnsCursor()) {
          result = executeForCursor(sqlSession, args);
        }
        // 其它情况
        else {
          // 获得参数列表
          Object param = method.convertArgsToSqlCommandParam(args);
          // 执行
          result = sqlSession.selectOne(command.getName(), param);
          // 方法返回 Optional && 返回值是null ; 返回 Optional null对象
          if (method.returnsOptional()
              && (result == null || !method.getReturnType().equals(result.getClass()))) {
            result = Optional.ofNullable(result);
          }
        }
        break;
        // 刷新缓存
      case FLUSH:
        result = sqlSession.flushStatements();
        break;
      default:
        throw new BindingException("Unknown execution method for: " + command.getName());
    }
    // 如果返回值是 null, 并且返回类型是基础类型， 此时很尴尬， 不能返回null, 只能抛个异常了
    if (result == null && method.getReturnType().isPrimitive() && !method.returnsVoid()) {
      throw new BindingException("Mapper method '" + command.getName()
          + " attempted to return null from a method with a primitive return type (" + method.getReturnType() + ").");
    }
    return result;
  }

  /**
   * 根据resultType 转换 sql执行的返回值
   * @param rowCount
   * @return
   */
  private Object rowCountResult(int rowCount) {
    final Object result;
    if (method.returnsVoid()) {
      result = null;
    } else if (Integer.class.equals(method.getReturnType()) || Integer.TYPE.equals(method.getReturnType())) {
      result = rowCount;
    } else if (Long.class.equals(method.getReturnType()) || Long.TYPE.equals(method.getReturnType())) {
      result = (long)rowCount;
    } else if (Boolean.class.equals(method.getReturnType()) || Boolean.TYPE.equals(method.getReturnType())) {
      result = rowCount > 0;
    } else {
      throw new BindingException("Mapper method '" + command.getName() + "' has an unsupported return type: " + method.getReturnType());
    }
    return result;
  }

  /**
   * 执行
   * @param sqlSession
   * @param args
   */
  private void executeWithResultHandler(SqlSession sqlSession, Object[] args) {
    // 尝试从 configuration 中获取 MappedStatement
    MappedStatement ms = sqlSession.getConfiguration().getMappedStatement(command.getName());
    //
    if (!StatementType.CALLABLE.equals(ms.getStatementType())
        && void.class.equals(ms.getResultMaps().get(0).getType())) {
      throw new BindingException("method " + command.getName()
          + " needs either a @ResultMap annotation, a @ResultType annotation,"
          + " or a resultType attribute in XML so a ResultHandler can be used as a parameter.");
    }
    // 获得方法参数列表 Map
    Object param = method.convertArgsToSqlCommandParam(args);
    // 执行 select 方法
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      sqlSession.select(command.getName(), param, rowBounds, method.extractResultHandler(args));
    } else {
      sqlSession.select(command.getName(), param, method.extractResultHandler(args));
    }
  }

  /**
   * 执行 返回集合或者数组的 mapper方法
   * @param sqlSession
   * @param args
   * @param <E>
   * @return
   */
  private <E> Object executeForMany(SqlSession sqlSession, Object[] args) {
    List<E> result;
    // 获得方法参数
    Object param = method.convertArgsToSqlCommandParam(args);
    // 执行 select 方法
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      result = sqlSession.selectList(command.getName(), param, rowBounds);
    } else {
      result = sqlSession.selectList(command.getName(), param);
    }
    // issue #510 Collections & arrays support
    // 方法返回类型；
    if (!method.getReturnType().isAssignableFrom(result.getClass())) {
      // 返回类型是一个数组
      if (method.getReturnType().isArray()) {
        return convertToArray(result);
      }
      // 返回类型是一个集合
      else {
        return convertToDeclaredCollection(sqlSession.getConfiguration(), result);
      }
    }
    return result;
  }

  private <T> Cursor<T> executeForCursor(SqlSession sqlSession, Object[] args) {
    Cursor<T> result;
    // 获得参数列表
    Object param = method.convertArgsToSqlCommandParam(args);
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      result = sqlSession.selectCursor(command.getName(), param, rowBounds);
    } else {
      result = sqlSession.selectCursor(command.getName(), param);
    }
    return result;
  }

  /**
   * 把list 封装到集合中
   * @param config
   * @param list
   * @param <E>
   * @return
   */
  private <E> Object convertToDeclaredCollection(Configuration config, List<E> list) {
    // 根据类型创建对象
    Object collection = config.getObjectFactory().create(method.getReturnType());
    // 创建 MetaObject 对象，
    MetaObject metaObject = config.newMetaObject(collection);
    // 添加到 list 到 Collection 中； MetaObject 中的ObjectWrapper 都是用的同一个Collection 对象
    metaObject.addAll(list);
    return collection;
  }

  @SuppressWarnings("unchecked")
  private <E> Object convertToArray(List<E> list) {
    // 获得返回集合的元素类型
    Class<?> arrayComponentType = method.getReturnType().getComponentType();
    // 创建一个新数组
    Object array = Array.newInstance(arrayComponentType, list.size());
    // 是基本类型
    if (arrayComponentType.isPrimitive()) {
      // 赋值
      for (int i = 0; i < list.size(); i++) {
        Array.set(array, i, list.get(i));
      }
      return array;
    }
    // 不是基本类型
    else {
      return list.toArray((E[])array);
    }
  }

  /** 执行返回Map的方法 */
  private <K, V> Map<K, V> executeForMap(SqlSession sqlSession, Object[] args) {
    Map<K, V> result;
    // 获得参数列表
    Object param = method.convertArgsToSqlCommandParam(args);
    // 执行方法
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      result = sqlSession.selectMap(command.getName(), param, method.getMapKey(), rowBounds);
    } else {
      result = sqlSession.selectMap(command.getName(), param, method.getMapKey());
    }
    return result;
  }

  /**
   * 继承 HashMap ,重写 get方法
   * @param <V>
   */
  public static class ParamMap<V> extends HashMap<String, V> {

    private static final long serialVersionUID = -2212268410512043556L;

    @Override
    public V get(Object key) {
      if (!super.containsKey(key)) {
        throw new BindingException("Parameter '" + key + "' not found. Available parameters are " + keySet());
      }
      return super.get(key);
    }

  }

  /**
   * sql 语句
   */
  public static class SqlCommand {

    /** 名称 */
    private final String name;
    /** sql语句类型 */
    private final SqlCommandType type;

    public SqlCommand(Configuration configuration, Class<?> mapperInterface, Method method) {
      final String methodName = method.getName();
      final Class<?> declaringClass = method.getDeclaringClass();
      // 获得 MappedStatement 对象
      MappedStatement ms = resolveMappedStatement(mapperInterface, methodName, declaringClass,
          configuration);
      if (ms == null) {
        // 如果有 @Flush 注解，则标记为 Flush 类型
        if (method.getAnnotation(Flush.class) != null) {
          name = null;
          type = SqlCommandType.FLUSH;
        } else {
          throw new BindingException("Invalid bound statement (not found): "
              + mapperInterface.getName() + "." + methodName);
        }
      }
      // 找到MapperStatement
      else {
        name = ms.getId();
        type = ms.getSqlCommandType();
        if (type == SqlCommandType.UNKNOWN) {
          throw new BindingException("Unknown execution method for: " + name);
        }
      }
    }

    public String getName() {
      return name;
    }

    public SqlCommandType getType() {
      return type;
    }

    /**
     * 获得 MappedStatement
     *
     * @param mapperInterface
     * @param methodName
     * @param declaringClass
     * @param configuration
     * @return
     */
    private MappedStatement resolveMappedStatement(Class<?> mapperInterface, String methodName,
        Class<?> declaringClass, Configuration configuration) {
      // MapperName.methodName
      String statementId = mapperInterface.getName() + "." + methodName;
      // 配置对象包含 statementId; 直接从配置对象中获取
      if (configuration.hasStatement(statementId)) {
        return configuration.getMappedStatement(statementId);
      }
      // mapper接口和 方法所在类相同， 找不到 返回null
      else if (mapperInterface.equals(declaringClass)) {
        return null;
      }
      // 遍历父类接口
      for (Class<?> superInterface : mapperInterface.getInterfaces()) {
        // declaringClass 与 superInterface相等， 或者是其超类/接口
        if (declaringClass.isAssignableFrom(superInterface)) {
          // 递归遍历 父类接口, 因为该MethodName 定义在父类中
          MappedStatement ms = resolveMappedStatement(superInterface, methodName,
              declaringClass, configuration);
          // 找到返回
          if (ms != null) {
            return ms;
          }
        }
      }
      // 真的找不到， 返回null
      return null;
    }
  }

  /**
   * 方法签名
   */
  public static class MethodSignature {

    private final boolean returnsMany;        // 是否返回集合或者数组
    private final boolean returnsMap;         // 是否返回 Map
    private final boolean returnsVoid;        // 是否无放回
    private final boolean returnsCursor;      // 是否返回 {@link Cursor}
    private final boolean returnsOptional;    // 是否返回 {@link java.util.Optional}
    private final Class<?> returnType;        // 返回类型
    private final String mapKey;              // 返回方法上的@MapKey 表示的值, 前提返回类型为 Map
    private final Integer resultHandlerIndex; // 获得 {@link ResultHandler }自定义的返回结果处理； 在参数中的位置
    private final Integer rowBoundsIndex;     // 获得 {@link ResultBounds}逻辑分页； 在参数中的位置
    private final ParamNameResolver paramNameResolver;  // 参数名称解析器


    public MethodSignature(Configuration configuration, Class<?> mapperInterface, Method method) {
      // 解析方法的返回类型
      Type resolvedReturnType = TypeParameterResolver.resolveReturnType(method, mapperInterface);
      // 设置方法返回类型
      if (resolvedReturnType instanceof Class<?>) {
        this.returnType = (Class<?>) resolvedReturnType;
      } else if (resolvedReturnType instanceof ParameterizedType) {
        this.returnType = (Class<?>) ((ParameterizedType) resolvedReturnType).getRawType();
      } else {
        this.returnType = method.getReturnType();
      }

      this.returnsVoid = void.class.equals(this.returnType);
      this.returnsMany = configuration.getObjectFactory().isCollection(this.returnType) || this.returnType.isArray();
      this.returnsCursor = Cursor.class.equals(this.returnType);
      this.returnsOptional = Optional.class.equals(this.returnType);

      this.mapKey = getMapKey(method);
      this.returnsMap = this.mapKey != null;
      this.rowBoundsIndex = getUniqueParamIndex(method, RowBounds.class);
      this.resultHandlerIndex = getUniqueParamIndex(method, ResultHandler.class);
      this.paramNameResolver = new ParamNameResolver(configuration, method);
    }

    /**
     * 获得参数列表 key:参数名， value:参数值
     * @param args
     * @return
     */
    public Object convertArgsToSqlCommandParam(Object[] args) {
      return paramNameResolver.getNamedParams(args);
    }

    public boolean hasRowBounds() {
      return rowBoundsIndex != null;
    }

    public RowBounds extractRowBounds(Object[] args) {
      return hasRowBounds() ? (RowBounds) args[rowBoundsIndex] : null;
    }

    public boolean hasResultHandler() {
      return resultHandlerIndex != null;
    }

    public ResultHandler extractResultHandler(Object[] args) {
      return hasResultHandler() ? (ResultHandler) args[resultHandlerIndex] : null;
    }

    public String getMapKey() {
      return mapKey;
    }

    public Class<?> getReturnType() {
      return returnType;
    }

    public boolean returnsMany() {
      return returnsMany;
    }

    public boolean returnsMap() {
      return returnsMap;
    }

    public boolean returnsVoid() {
      return returnsVoid;
    }

    public boolean returnsCursor() {
      return returnsCursor;
    }

    /**
     * return whether return type is {@code java.util.Optional}.
     * @return return {@code true}, if return type is {@code java.util.Optional}
     * @since 3.5.0
     */
    public boolean returnsOptional() {
      return returnsOptional;
    }

    /**
     * 获得指定方法参数类型在参数列表上的位置
     * @param method
     * @param paramType
     * @return
     */
    private Integer getUniqueParamIndex(Method method, Class<?> paramType) {
      Integer index = null;
      final Class<?>[] argTypes = method.getParameterTypes();
      for (int i = 0; i < argTypes.length; i++) {
        if (paramType.isAssignableFrom(argTypes[i])) {
          if (index == null) {
            index = i;
          } else {
            throw new BindingException(method.getName() + " cannot have multiple " + paramType.getSimpleName() + " parameters");
          }
        }
      }
      return index;
    }

    /**
     * 获得 @MapKey 的值
     * @param method
     * @return
     */
    private String getMapKey(Method method) {
      String mapKey = null;
      // 方法的返回类似是否是Map 或者其子类
      if (Map.class.isAssignableFrom(method.getReturnType())) {
        // 获得方法上的 @MapKey 注解
        final MapKey mapKeyAnnotation = method.getAnnotation(MapKey.class);
        // 获得 @MapKey 的值
        if (mapKeyAnnotation != null) {
          mapKey = mapKeyAnnotation.value();
        }
      }
      return mapKey;
    }
  }

}
