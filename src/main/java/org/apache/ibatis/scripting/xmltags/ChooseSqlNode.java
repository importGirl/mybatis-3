/**
 *    Copyright 2009-2017 the original author or authors.
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
package org.apache.ibatis.scripting.xmltags;

import java.util.List;

/**
 * 相当于java switch 关键字；
 * @author Clinton Begin
 */
public class ChooseSqlNode implements SqlNode {
  private final SqlNode defaultSqlNode;     // <otherwise/> 对应对sqlNode节点
  private final List<SqlNode> ifSqlNodes;   // <when/> sqlNode列表

  public ChooseSqlNode(List<SqlNode> ifSqlNodes, SqlNode defaultSqlNode) {
    this.ifSqlNodes = ifSqlNodes;
    this.defaultSqlNode = defaultSqlNode;
  }

  /**
   * switch() case break; default;
   * @param context
   * @return
   */
  @Override
  public boolean apply(DynamicContext context) {
    // 遍历
    for (SqlNode sqlNode : ifSqlNodes) {
      // 执行<when/>标签
      if (sqlNode.apply(context)) {
        return true;
      }
    }
    // 执行 default
    if (defaultSqlNode != null) {
      defaultSqlNode.apply(context);
      return true;
    }
    return false;
  }
}
