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
package org.apache.ibatis.type;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Integer 类型的 TypeHandler 实现类
 * @author Clinton Begin
 */
public class IntegerTypeHandler extends BaseTypeHandler<Integer> {

  /**
   * 直接设置 ps 的参数
   * @param ps
   * @param i
   * @param parameter
   * @param jdbcType
   * @throws SQLException
   */
  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, Integer parameter, JdbcType jdbcType)
      throws SQLException {
    ps.setInt(i, parameter);
  }

  /**
   * 通过 columnName 获取 值
   * @param rs
   * @param columnName
   * @return
   * @throws SQLException
   */
  @Override
  public Integer getNullableResult(ResultSet rs, String columnName)
      throws SQLException {
    // 获得指定字段的值
    int result = rs.getInt(columnName);
    // 返回 result
    return result == 0 && rs.wasNull() ? null : result;
  }

  /**
   * 通过 columnIndex 获取 值
   * @param rs
   * @param columnIndex
   * @return
   * @throws SQLException
   */
  @Override
  public Integer getNullableResult(ResultSet rs, int columnIndex)
      throws SQLException {
    int result = rs.getInt(columnIndex);
    return result == 0 && rs.wasNull() ? null : result;
  }

  /**
   * 通过 columnIndex 获取 值
   * @param cs
   * @param columnIndex
   * @return
   * @throws SQLException
   */
  @Override
  public Integer getNullableResult(CallableStatement cs, int columnIndex)
      throws SQLException {
    int result = cs.getInt(columnIndex);
    return result == 0 && cs.wasNull() ? null : result;
  }
}
