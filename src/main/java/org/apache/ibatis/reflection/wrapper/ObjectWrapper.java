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
package org.apache.ibatis.reflection.wrapper;

import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

import java.util.List;

/**
 * 对象包装器接口
 *
 * 基于 MetaClass 工具类， 定义对指定对象对各种操作
 * @author Clinton Begin
 */
public interface ObjectWrapper {

  // 获取指定属性值
  Object get(PropertyTokenizer prop);

  // 设置指定属性值
  void set(PropertyTokenizer prop, Object value);

  /**
   * {@link MetaClass#findProperty(String,boolean)}
   */
  String findProperty(String name, boolean useCamelCaseMapping);

  /**
   * {@link MetaClass#getGetterNames()}
   */
  String[] getGetterNames();

  /**
   * {@link MetaClass#getSetterNames()}
   */
  String[] getSetterNames();

  /**
   * {@link MetaClass#getSetterType(String)} ()}
   */
  Class<?> getSetterType(String name);

  /**
   * {@link MetaClass#getGetterType(String)} ()}
   */
  Class<?> getGetterType(String name);

  /**
   * {@link MetaClass#hasSetter(String)} ()}
   */
  boolean hasSetter(String name);

  /**
   * {@link MetaClass#hasGetter(String)} ()}
   */
  boolean hasGetter(String name);

  /**
   * {@link MetaObject#forObject(Object, ObjectFactory, ObjectWrapperFactory, ReflectorFactory)} ()}
   */
  MetaObject instantiatePropertyValue(String name, PropertyTokenizer prop, ObjectFactory objectFactory);

  // 是否是集合
  boolean isCollection();

  // 添加元素
  void add(Object element);

  // 添加多个元素
  <E> void addAll(List<E> element);

}
