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
package org.apache.ibatis.mapping;

import java.sql.ResultSet;

/**
 * @author Clinton Begin
 */
public enum ResultSetType {
  /**
   * behavior with same as unset (driver dependent).
   *
   * @since 3.5.0
   */
  DEFAULT(-1),
  /**
   *   // 默认的cursor 类型，仅仅支持结果集forward ，不支持backforward ，random ，last ，first 等操作。
   */
  FORWARD_ONLY(ResultSet.TYPE_FORWARD_ONLY),
  /**
   * 支持结果集backforward ，random ，last ，first 等操作，对其它session 对数据库中数据做出的更改是不敏感的。
   *
   * 实现方法：从数据库取出数据后，会把全部数据缓存到cache 中，对结果集的后续操作，是操作的cache 中的数据，数据库中记录发生变化后，不影响cache 中的数据，所以ResultSet 对结果集中的数据是INSENSITIVE 的。
   */
  SCROLL_INSENSITIVE(ResultSet.TYPE_SCROLL_INSENSITIVE),
  /**
   * 支持结果集backforward ，random ，last ，first 等操作，对其它session 对数据库中数据做出的更改是敏感的，即其他session 修改了数据库中的数据，会反应到本结果集中。
   */
  SCROLL_SENSITIVE(ResultSet.TYPE_SCROLL_SENSITIVE);

  private final int value;

  ResultSetType(int value) {
    this.value = value;
  }

  public int getValue() {
    return value;
  }
}
