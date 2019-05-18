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
package org.apache.ibatis.logging.jdbc;

import org.apache.ibatis.logging.Log;
import org.apache.ibatis.reflection.ExceptionUtil;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashSet;
import java.util.Set;
import java.util.StringJoiner;

/**
 * ResultSet proxy to add logging.
 *基于 ResultSet 的代理类
 * @author Clinton Begin
 * @author Eduardo Macarron
 *
 */
public final class ResultSetLogger extends BaseJdbcLogger implements InvocationHandler {

  private static final Set<Integer> BLOB_TYPES = new HashSet<>();
  private boolean first = true;
  private int rows;
  private final ResultSet rs;
  private final Set<Integer> blobColumns = new HashSet<>();

  // 二进制类型
  static {
    BLOB_TYPES.add(Types.BINARY);
    BLOB_TYPES.add(Types.BLOB);
    BLOB_TYPES.add(Types.CLOB);
    BLOB_TYPES.add(Types.LONGNVARCHAR);
    BLOB_TYPES.add(Types.LONGVARBINARY);
    BLOB_TYPES.add(Types.LONGVARCHAR);
    BLOB_TYPES.add(Types.NCLOB);
    BLOB_TYPES.add(Types.VARBINARY);
  }

  private ResultSetLogger(ResultSet rs, Log statementLog, int queryStack) {
    super(statementLog, queryStack);
    this.rs = rs;
  }

  /**
   * 代理方法
   * @param proxy
   * @param method
   * @param params
   * @return
   * @throws Throwable
   */
  @Override
  public Object invoke(Object proxy, Method method, Object[] params) throws Throwable {
    try {
      // Object 方法
      if (Object.class.equals(method.getDeclaringClass())) {
        return method.invoke(this, params);
      }
      // 调用 方法
      Object o = method.invoke(rs, params);
      // 如果 next 方法
      if ("next".equals(method.getName())) {
        if ((Boolean) o) {
          rows++;
          if (isTraceEnabled()) {
            ResultSetMetaData rsmd = rs.getMetaData();
            final int columnCount = rsmd.getColumnCount();
            // 如果是第一个元素，打印带有前缀的日志
            if (first) {
              first = false;
              printColumnHeaders(rsmd, columnCount);
            }
            // 如果不是第一个元素， 打印列的值
            printColumnValues(columnCount);
          }
        }
        //
        else {
          debug("     Total: " + rows, false);
        }
      }
      clearColumnInfo();
      return o;
    } catch (Throwable t) {
      throw ExceptionUtil.unwrapThrowable(t);
    }
  }

  /**
   * 打印日志前缀 " Columns: id,name,age "
   * @param rsmd
   * @param columnCount
   * @throws SQLException
   */
  private void printColumnHeaders(ResultSetMetaData rsmd, int columnCount) throws SQLException {
    // 创建可设置 前缀，后缀， 分隔符号的 String
    StringJoiner row = new StringJoiner(", ", "   Columns: ", "");
    for (int i = 1; i <= columnCount; i++) {
      // 是否二进制列
      if (BLOB_TYPES.contains(rsmd.getColumnType(i))) {
        blobColumns.add(i);
      }
      // 获得列头
      row.add(rsmd.getColumnLabel(i));
    }
    // 打印
    trace(row.toString(), false);
  }

  /**
   * 打印列的值：" Row: 1, wdg, 19 "
   * @param columnCount
   */
  private void printColumnValues(int columnCount) {
    StringJoiner row = new StringJoiner(", ", "       Row: ", "");
    for (int i = 1; i <= columnCount; i++) {
      try {
        // 是否二进制列
        if (blobColumns.contains(i)) {
          row.add("<<BLOB>>");
        } else {
          row.add(rs.getString(i));
        }
      } catch (SQLException e) {
        // generally can't call getString() on a BLOB column
        row.add("<<Cannot Display>>");
      }
    }
    // 打印
    trace(row.toString(), false);
  }

  /**
   * Creates a logging version of a ResultSet.
   *
   * @param rs - the ResultSet to proxy
   * @return - the ResultSet with logging
   */
  public static ResultSet newInstance(ResultSet rs, Log statementLog, int queryStack) {
    InvocationHandler handler = new ResultSetLogger(rs, statementLog, queryStack);
    ClassLoader cl = ResultSet.class.getClassLoader();
    return (ResultSet) Proxy.newProxyInstance(cl, new Class[]{ResultSet.class}, handler);
  }

  /**
   * Get the wrapped result set.
   *
   * @return the resultSet
   */
  public ResultSet getRs() {
    return rs;
  }

}
