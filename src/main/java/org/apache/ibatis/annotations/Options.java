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
package org.apache.ibatis.annotations;

import org.apache.ibatis.mapping.ResultSetType;
import org.apache.ibatis.mapping.StatementType;

import java.lang.annotation.*;

/**
 * 操作可选项； 配合其它@select... 注解使用
 * @author Clinton Begin
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Options {
  /**
   * The options for the {@link Options#flushCache()}.
   * The default is {@link FlushCachePolicy#DEFAULT}
   *
   */
  enum FlushCachePolicy {
    /** <code>false</code> for select statement; <code>true</code> for insert/update/delete statement. */
    DEFAULT,
    /** Flushes cache regardless of the statement type. */
    TRUE,
    /** Does not flush cache regardless of the statement type. */
    FALSE
  }

  /** 是否使用缓存 */
  boolean useCache() default true;

  /** 刷新缓存 */
  FlushCachePolicy flushCache() default FlushCachePolicy.DEFAULT;

  /** 返回集合类型 */
  ResultSetType resultSetType() default ResultSetType.DEFAULT;

  /** 语句类型 */
  StatementType statementType() default StatementType.PREPARED;

  /** 加载数量 */
  int fetchSize() default -1;

  /** 超时时间 */
  int timeout() default -1;

  /** 是否生成主键 */
  boolean useGeneratedKeys() default false;

  /** 主键在java 中的字段 */
  String keyProperty() default "";

  /** 主键在数据库中的字段 */
  String keyColumn() default "";

  /** 结果级 */
  String resultSets() default "";
}
