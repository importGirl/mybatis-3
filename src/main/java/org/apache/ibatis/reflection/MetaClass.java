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

import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;

/**
 * 类的元数据， 基于 Reflector 和 PropertyTokenizer ， 提供对类的各种骚操作
 * @author Clinton Begin
 */
public class MetaClass {

  // 工厂方法
  private final ReflectorFactory reflectorFactory;
  // 反射器
  private final Reflector reflector;

  /**
   * 一个 metaclass 对应一个 class 对象
   * @param type
   * @param reflectorFactory
   */
  private MetaClass(Class<?> type, ReflectorFactory reflectorFactory) {
    this.reflectorFactory = reflectorFactory;
    this.reflector = reflectorFactory.findForClass(type);
  }

  // 创建MetaClass 对象
  public static MetaClass forClass(Class<?> type, ReflectorFactory reflectorFactory) {
    return new MetaClass(type, reflectorFactory);
  }

  // 创建指定属性的 MetaClass
  public MetaClass metaClassForProperty(String name) {
    // 获得属性的类
    Class<?> propType = reflector.getGetterType(name);
    // 创建 MetaClass 对象
    return MetaClass.forClass(propType, reflectorFactory);
  }

  // 根据表达式获得属性
  public String findProperty(String name) {
    // 构建属性
    StringBuilder prop = buildProperty(name, new StringBuilder());
    return prop.length() > 0 ? prop.toString() : null;
  }

  //
  public String findProperty(String name, boolean useCamelCaseMapping) {
    // 下划线转驼峰， 但是没有转成驼峰； 存到map key是不区分大小写到
    if (useCamelCaseMapping) {
      name = name.replace("_", "");
    }
    return findProperty(name);
  }

  public String[] getGetterNames() {
    return reflector.getGetablePropertyNames();
  }

  public String[] getSetterNames() {
    return reflector.getSetablePropertyNames();
  }


  public Class<?> getSetterType(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      MetaClass metaProp = metaClassForProperty(prop.getName());
      return metaProp.getSetterType(prop.getChildren());
    } else {
      return reflector.getSetterType(prop.getName());
    }
  }

  public Class<?> getGetterType(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      MetaClass metaProp = metaClassForProperty(prop);
      return metaProp.getGetterType(prop.getChildren());
    }
    // issue #506. Resolve the type inside a Collection Object
    return getGetterType(prop);
  }


  private MetaClass metaClassForProperty(PropertyTokenizer prop) {
    Class<?> propType = getGetterType(prop);
    return MetaClass.forClass(propType, reflectorFactory);
  }

  // 获得getter 类型
  private Class<?> getGetterType(PropertyTokenizer prop) {
    // 反射器获取
    Class<?> type = reflector.getGetterType(prop.getName());
    // 如果获取数组是某个位置的元素， 则获取其泛型， 例如： List[0].field 那么会解析List是什么类型，这样才好通过该类型，继续获得field
    if (prop.getIndex() != null && Collection.class.isAssignableFrom(type)) {
      // 获得返回类型 List
      Type returnType = getGenericGetterType(prop.getName());
      // 返回类型是否属于 参数化泛型 List<String>、 Set<Long>、 Map<String,String>
      if (returnType instanceof ParameterizedType) {
        // 获得参数化泛型的列表 Type[]{String}、Type[]{ Long}
        Type[] actualTypeArguments = ((ParameterizedType) returnType).getActualTypeArguments();
        // 如果存在且不是Map 集合
        if (actualTypeArguments != null && actualTypeArguments.length == 1) {
          // 获得参数化泛型 String Long
          returnType = actualTypeArguments[0];
          // 判断参数化泛型是否是 Class类型，包括 void、基本类型、枚举； 除了泛型 T E ?
          if (returnType instanceof Class) {
            // 直接强转
            type = (Class<?>) returnType;// 这里就是集合中元素等类型了
          }
          // 如果是参数化泛型 Set<String>、List<String> 等
          else if (returnType instanceof ParameterizedType) {
            // 先强转成 ParameterizedType， 再返回承载该泛型的对象 Set, 再强转成 Class<?>
            type = (Class<?>) ((ParameterizedType) returnType).getRawType();// 集合中元素的类型
          }
        }
      }
    }
    return type;
  }

  private Type getGenericGetterType(String propertyName) {
    try {
      // 获得 Invoker 对象
      Invoker invoker = reflector.getGetInvoker(propertyName);
      // 如果是MethodInvoker 对象， 则说明是 getting 方法， 解析方法返回类型
      if (invoker instanceof MethodInvoker) {
        Field _method = MethodInvoker.class.getDeclaredField("method");
        _method.setAccessible(true);
        Method method = (Method) _method.get(invoker);// 获得 method 字段
        return TypeParameterResolver.resolveReturnType(method, reflector.getType());// TODO
      }
      // 如果是 GetFieldInvoker 对象，则说明是 Field， 直接访问
      else if (invoker instanceof GetFieldInvoker) {
        Field _field = GetFieldInvoker.class.getDeclaredField("field");
        _field.setAccessible(true);
        Field field = (Field) _field.get(invoker);
        return TypeParameterResolver.resolveFieldType(field, reflector.getType());
      }
    } catch (NoSuchFieldException | IllegalAccessException ignored) {
    }
    return null;
  }

  public boolean hasSetter(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      if (reflector.hasSetter(prop.getName())) {
        MetaClass metaProp = metaClassForProperty(prop.getName());
        return metaProp.hasSetter(prop.getChildren());
      } else {
        return false;
      }
    } else {
      return reflector.hasSetter(prop.getName());
    }
  }

  // 是否有getter 方法， 递归子属性
  public boolean hasGetter(String name) {
    // 创建 属性分词器
    PropertyTokenizer prop = new PropertyTokenizer(name);
    // 属性是否有下一个
    if (prop.hasNext()) {
      // 反射器是否有getter 方法
      if (reflector.hasGetter(prop.getName())) {
        // 创建 MetaClass 对象
        MetaClass metaProp = metaClassForProperty(prop);
        // 递归子表达式
        return metaProp.hasGetter(prop.getChildren());
      } else {
        return false;
      }
    } else {
      return reflector.hasGetter(prop.getName());
    }
  }

  public Invoker getGetInvoker(String name) {
    return reflector.getGetInvoker(name);
  }

  public Invoker getSetInvoker(String name) {
    return reflector.getSetInvoker(name);
  }

  /**
   *  构建属性
   * @param name
   * @param builder
   * @return
   */
  private StringBuilder buildProperty(String name, StringBuilder builder) {
    // 创建 PropertyTokenizer ，对name 进行分词
    PropertyTokenizer prop = new PropertyTokenizer(name);
    // 有子表达式
    if (prop.hasNext()) {
      // 获得属性名，并添加到builder中
      String propertyName = reflector.findPropertyName(prop.getName());
      if (propertyName != null) {
        builder.append(propertyName);
        builder.append(".");
        // 创建 MetaClass 对象
        MetaClass metaProp = metaClassForProperty(propertyName);
        // 解析子表达式， 并添加到 builder 中
        metaProp.buildProperty(prop.getChildren(), builder);
      }
    } else {
      // 获得属性名， 并添加到builder中
      String propertyName = reflector.findPropertyName(name);
      if (propertyName != null) {
        builder.append(propertyName);
      }
    }
    return builder;
  }

  public boolean hasDefaultConstructor() {
    return reflector.hasDefaultConstructor();
  }

}
