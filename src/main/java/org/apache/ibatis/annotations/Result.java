/**
 *    Copyright 2009-2016 the original author or authors.
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
package org.apache.ibatis.annotations;

import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.UnknownTypeHandler;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Clinton Begin
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface Result {

  /**
   * 是否是 id 字段
   * @return
   */
  boolean id() default false;

  /**
   * 列名
   * @return
   */
  String column() default "";

  /**
   * 对象属性
   * @return
   */
  String property() default "";

  /**
   * 对象属性类型
   * @return
   */
  Class<?> javaType() default void.class;

  /**
   * jdbc 类型
   * @return
   */
  JdbcType jdbcType() default JdbcType.UNDEFINED;

  /**
   * 使用的 TypeHandler 处理器
   * @return
   */
  Class<? extends TypeHandler> typeHandler() default UnknownTypeHandler.class;


  One one() default @One;

  Many many() default @Many;
}
