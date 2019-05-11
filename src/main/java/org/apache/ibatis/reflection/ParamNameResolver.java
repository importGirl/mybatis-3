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
package org.apache.ibatis.reflection;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * 参数名解析器
 */
public class ParamNameResolver {

  // 通用名称前缀
  private static final String GENERIC_NAME_PREFIX = "param";

  /**
   * <p>
   * The key is the index and the value is the name of the parameter.<br />
   * The name is obtained from {@link Param} if specified. When {@link Param} is not specified,
   * the parameter index is used. Note that this index could be different from the actual index
   * when the method has special parameters (i.e. {@link RowBounds} or {@link ResultHandler}).
   * </p>
   * <ul>
   * <li>aMethod(@Param("M") int a, @Param("N") int b) -&gt; {{0, "M"}, {1, "N"}}</li>
   * <li>aMethod(int a, int b) -&gt; {{0, "0"}, {1, "1"}}</li>
   * <li>aMethod(int a, RowBounds rb, int b) -&gt; {{0, "0"}, {2, "1"}}</li>
   * </ul>
   */
  private final SortedMap<Integer, String> names;

  // 是否有@param 注解
  private boolean hasParamAnnotation;

  public ParamNameResolver(Configuration config, Method method) {
    // 获取参数类型
    final Class<?>[] paramTypes = method.getParameterTypes();
    // 获取方法参数注解 二维数组（1个参数可以有多个注解）
    final Annotation[][] paramAnnotations = method.getParameterAnnotations();
    final SortedMap<Integer, String> map = new TreeMap<>();
    // 参数数量
    int paramCount = paramAnnotations.length;
    // get names from @Param annotations
    for (int paramIndex = 0; paramIndex < paramCount; paramIndex++) {
      // 是否特殊参数， 则直接跳过
      if (isSpecialParameter(paramTypes[paramIndex])) {
        // skip special parameters
        continue;
      }
      String name = null;
      // 循环每个参数上的注解
      for (Annotation annotation : paramAnnotations[paramIndex]) {
        // 如果是@Param 注解
        if (annotation instanceof Param) {
          // 设置为true
          hasParamAnnotation = true;
          // 获取@param 注解的值
          name = ((Param) annotation).value();
          break;// 跳出这个参数注解列表的循环； 解析下个参数
        }
      }
      // 不存在 @param
      if (name == null) {
        // @Param was not specified.
        // 是否使用实际参数名称代替 @param TODO
        if (config.isUseActualParamName()) {
          name = getActualParamName(method, paramIndex);
        }
        if (name == null) {
          // use the parameter index as the name ("0", "1", ...)
          // gcode issue #71
          // 使用参数下标作为 name ("0", "1", ...)
          name = String.valueOf(map.size());
        }
      }
      // 放入map 集合
      map.put(paramIndex, name);
    }
    // 设置为不可修改的 map 集合
    names = Collections.unmodifiableSortedMap(map);
  }

  private String getActualParamName(Method method, int paramIndex) {
    return ParamNameUtil.getParamNames(method).get(paramIndex);
  }

  /**
   * 是否特殊参数   RowBounds | ResultHandler 的子类
   * @param clazz
   * @return
   */
  private static boolean isSpecialParameter(Class<?> clazz) {
    return RowBounds.class.isAssignableFrom(clazz) || ResultHandler.class.isAssignableFrom(clazz);
  }

  /**
   * Returns parameter names referenced by SQL providers.
   */
  public String[] getNames() {
    return names.values().toArray(new String[0]);
  }

  /**
   * <p>
   * A single non-special parameter is returned without a name.
   * Multiple parameters are named using the naming rule.
   * In addition to the default names, this method also adds the generic names (param1, param2,
   * ...).
   *
   * 获取参数名于值的映射
   *
   * </p>
   */
  public Object getNamedParams(Object[] args) {
    final int paramCount = names.size();
    // 无参数，则返回null
    if (args == null || paramCount == 0) {
      return null;
    }
    // 只有一个非注解的参数，直接返回首元素
    else if (!hasParamAnnotation && paramCount == 1) {
      return args[names.firstKey()];
    }
    //
    else {
      /**
       * 集合
       * 组合1：key:参数名，value:参数值
       * 组合2：key:GENERIC_NAME_PREFIX + 参数顺序（1，2，3。。。）, value:参数值
       */
      final Map<String, Object> param = new ParamMap<>();
      int i = 0;
      // 遍历 names 集合
      for (Map.Entry<Integer, String> entry : names.entrySet()) {
        // 组合1，添加到param 集合中
        param.put(entry.getValue(), args[entry.getKey()]);
        // add generic param names (param1, param2, ...)
        // 组合2，添加到param 集合中 (param1, param2, ...)
        final String genericParamName = GENERIC_NAME_PREFIX + String.valueOf(i + 1);
        // ensure not to overwrite parameter named with @Param
        // 确认没有去覆盖 @param 中的参数名， 才进行put 操作
        if (!names.containsValue(genericParamName)) {
          param.put(genericParamName, args[entry.getKey()]);
        }
        i++;
      }
      return param;
    }
  }
}
