/**
 *    Copyright 2009-2018 the original author or authors.
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

import org.apache.ibatis.reflection.ExceptionUtil;
import org.apache.ibatis.session.SqlSession;

import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;

/**
 * Mapper 代理对象
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class MapperProxy<T> implements InvocationHandler, Serializable {

  private static final long serialVersionUID = -6424540398559729838L;
  private final SqlSession sqlSession;                    // sql 域
  private final Class<T> mapperInterface;                 // Mapper 接口
  private final Map<Method, MapperMethod> methodCache;    // Mapper 方法集合

  public MapperProxy(SqlSession sqlSession, Class<T> mapperInterface, Map<Method, MapperMethod> methodCache) {
    this.sqlSession = sqlSession;
    this.mapperInterface = mapperInterface;
    this.methodCache = methodCache;
  }

  /** 代理方法 */
  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    try {
      // Object方法
      if (Object.class.equals(method.getDeclaringClass())) {
        return method.invoke(this, args);
      }
      // 默认方法，则调用默认的方法
      else if (isDefaultMethod(method)) {
        return invokeDefaultMethod(proxy, method, args);
      }
    } catch (Throwable t) {
      throw ExceptionUtil.unwrapThrowable(t);
    }

    // 缓存Mapper 方法
    final MapperMethod mapperMethod = cachedMapperMethod(method);
    // 执行mapper方法
    return mapperMethod.execute(sqlSession, args);
  }

  /** 缓存Mapper 方法 */
  private MapperMethod cachedMapperMethod(Method method) {
    return methodCache.computeIfAbsent(method, k -> new MapperMethod(mapperInterface, method, sqlSession.getConfiguration()));
  }

  /**
   * java8 中反射调用 接口 default 方法的实现
   * @param proxy
   * @param method
   * @param args
   * @return
   * @throws Throwable
   */
  private Object invokeDefaultMethod(Object proxy, Method method, Object[] args)
      throws Throwable {

    final Constructor<MethodHandles.Lookup> constructor = MethodHandles.Lookup.class
        .getDeclaredConstructor(Class.class, int.class);
    if (!constructor.isAccessible()) {
      constructor.setAccessible(true);
    }
    final Class<?> declaringClass = method.getDeclaringClass();
    return constructor
        .newInstance(declaringClass,
            MethodHandles.Lookup.PRIVATE | MethodHandles.Lookup.PROTECTED
                | MethodHandles.Lookup.PACKAGE | MethodHandles.Lookup.PUBLIC)
        .unreflectSpecial(method, declaringClass).bindTo(proxy).invokeWithArguments(args);
  }

  /**
   * Backport of java.lang.reflect.Method#isDefault()
   * 判断 TODO
   */
  private boolean isDefaultMethod(Method method) {
    // 以整数形式返回此 Method 对象所表示方法的 Java 语言修饰符
    return (method.getModifiers()
        & (Modifier.ABSTRACT | Modifier.PUBLIC | Modifier.STATIC)) == Modifier.PUBLIC
        // 是否接口
        && method.getDeclaringClass().isInterface();
  }
}
