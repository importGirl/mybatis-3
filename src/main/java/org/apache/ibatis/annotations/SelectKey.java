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

import org.apache.ibatis.mapping.StatementType;

import java.lang.annotation.*;

/**
 * 通过sql 语句获得主键
 * @author Clinton Begin
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SelectKey {
  /** 主键 */
  String[] statement();

  /** java属性 */
  String keyProperty();

  /** db字段 */
  String keyColumn() default "";

  /** 是否在插入前，还是插入后 */
  boolean before();

  /** 返回类型 */
  Class<?> resultType();

  /** sql 语句类型 */
  StatementType statementType() default StatementType.PREPARED;
}
