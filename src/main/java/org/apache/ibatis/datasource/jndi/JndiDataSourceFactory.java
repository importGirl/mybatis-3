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
package org.apache.ibatis.datasource.jndi;

import org.apache.ibatis.datasource.DataSourceException;
import org.apache.ibatis.datasource.DataSourceFactory;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.util.Map.Entry;
import java.util.Properties;

/**
 * 基于 JNDI 实现 DataSourceFactory 接口
 * 这种数据源是为了实现在 EJB或者应用服务器这类容器中使用，容器可以集中或者在外部配置数据源；
 * 然后通过JNDI 上下文进行引用
 * @author Clinton Begin
 */
public class JndiDataSourceFactory implements DataSourceFactory {

  public static final String INITIAL_CONTEXT = "initial_context";     // 用来在InitialContext 中寻找上下文
  public static final String DATA_SOURCE = "data_source";             // 引用数据源实例位置的上下文路径
  public static final String ENV_PREFIX = "env.";                     // 可以通过env. 直接吧属性传递给初始上下文

  private DataSource dataSource;                                      // 数据源

  // 设置 dataSource 配置
  @Override
  public void setProperties(Properties properties) {
    try {
      InitialContext initCtx;
      // 获取环境配置
      Properties env = getEnvProperties(properties);
      // 创建 重量级初始化对象
      if (env == null) {
        initCtx = new InitialContext();
      } else {
        initCtx = new InitialContext(env);
      }

      // 判断配置对象是否含有 对应到前缀配置
      if (properties.containsKey(INITIAL_CONTEXT)
          && properties.containsKey(DATA_SOURCE)) {
        Context ctx = (Context) initCtx.lookup(properties.getProperty(INITIAL_CONTEXT));
        dataSource = (DataSource) ctx.lookup(properties.getProperty(DATA_SOURCE));
      } else if (properties.containsKey(DATA_SOURCE)) {
        dataSource = (DataSource) initCtx.lookup(properties.getProperty(DATA_SOURCE));
      }

    } catch (NamingException e) {
      throw new DataSourceException("There was an error configuring JndiDataSourceTransactionPool. Cause: " + e, e);
    }
  }

  @Override
  public DataSource getDataSource() {
    return dataSource;
  }


  // 获得环境属性
  private static Properties getEnvProperties(Properties allProps) {
    final String PREFIX = ENV_PREFIX;
    Properties contextProperties = null;
    for (Entry<Object, Object> entry : allProps.entrySet()) {
      String key = (String) entry.getKey();       // key
      String value = (String) entry.getValue();   // value
      // 是否 env. 开头
      if (key.startsWith(PREFIX)) {
        // 创建配置属性 contextProperties
        if (contextProperties == null) {
          contextProperties = new Properties();
        }
        // 保持属性到 contextProperties
        contextProperties.put(key.substring(PREFIX.length()), value);
      }
    }
    return contextProperties;
  }

}
