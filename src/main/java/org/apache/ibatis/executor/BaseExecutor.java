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
package org.apache.ibatis.executor;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.cache.impl.PerpetualCache;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.statement.StatementUtil;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.logging.jdbc.ConnectionLogger;
import org.apache.ibatis.mapping.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;
import org.apache.ibatis.type.TypeHandlerRegistry;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.apache.ibatis.executor.ExecutionPlaceholder.EXECUTION_PLACEHOLDER;

/**
 * 提供骨架方法
 * @author Clinton Begin
 */
public abstract class BaseExecutor implements Executor {

  private static final Log log = LogFactory.getLog(BaseExecutor.class);

  protected Transaction transaction;
  protected Executor wrapper;

  // 延迟加载的集合
  protected ConcurrentLinkedQueue<DeferredLoad> deferredLoads;
  // 一级缓存；本地缓存；不会过期
  protected PerpetualCache localCache;
  // 一级缓存：本地缓存输出参数：不会过期
  protected PerpetualCache localOutputParameterCache;
  // 全局配置
  protected Configuration configuration;

  // 查询次数； 没执行一次 doQuery（）都会++， 执行完 --
  protected int queryStack;
  // 是否关闭
  private boolean closed;

  protected BaseExecutor(Configuration configuration, Transaction transaction) {
    this.transaction = transaction;
    this.deferredLoads = new ConcurrentLinkedQueue<>();
    this.localCache = new PerpetualCache("LocalCache");
    this.localOutputParameterCache = new PerpetualCache("LocalOutputParameterCache");
    this.closed = false;
    this.configuration = configuration;
    this.wrapper = this;
  }

  @Override
  public Transaction getTransaction() {
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    return transaction;
  }

  /**
   * 关闭
   * @param forceRollback 是否强制回滚事务
   */
  @Override
  public void close(boolean forceRollback) {
    try {
      try {
        rollback(forceRollback);
      } finally {
        if (transaction != null) {
          transaction.close();
        }
      }
    } catch (SQLException e) {
      // Ignore.  There's nothing that can be done at this point.
      log.warn("Unexpected exception on closing transaction.  Cause: " + e);
    } finally {
      // 回收对象
      transaction = null;
      deferredLoads = null;
      localCache = null;
      localOutputParameterCache = null;
      closed = true;
    }
  }

  @Override
  public boolean isClosed() {
    return closed;
  }

  /**
   * 先清空缓存， 再更新db
   * @param ms
   * @param parameter
   * @return
   * @throws SQLException
   */
  @Override
  public int update(MappedStatement ms, Object parameter) throws SQLException {
    // 设置错误信息上下文； null+executing an update+insertAuthor
    ErrorContext.instance().resource(ms.getResource()).activity("executing an update").object(ms.getId());
    // 关闭
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }

    // 清空缓存
    clearLocalCache();
    // 更新db *子类实现*
    return doUpdate(ms, parameter);
  }

  /**
   * **子类实现**
   * @return
   * @throws SQLException
   */
  @Override
  public List<BatchResult> flushStatements() throws SQLException {
    return flushStatements(false);
  }


  public List<BatchResult> flushStatements(boolean isRollBack) throws SQLException {
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    return doFlushStatements(isRollBack);
  }

  /**
   * 查询操作
   * @param ms
   * @param parameter
   * @param rowBounds
   * @param resultHandler
   * @param <E>
   * @return
   * @throws SQLException
   */
  @Override
  public <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler) throws SQLException {
    // 获得执行 sql 对象
    BoundSql boundSql = ms.getBoundSql(parameter);
    // 生成 cacheKey
    CacheKey key = createCacheKey(ms, parameter, rowBounds, boundSql);
    // 查询
    return query(ms, parameter, rowBounds, resultHandler, key, boundSql);
  }

  /**
   * 查询
   * @param ms
   * @param parameter
   * @param rowBounds
   * @param resultHandler
   * @param key
   * @param boundSql
   * @param <E>
   * @return
   * @throws SQLException
   */
  @SuppressWarnings("unchecked")
  @Override
  public <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, CacheKey key, BoundSql boundSql) throws SQLException {
    // error上下文
    ErrorContext.instance().resource(ms.getResource()).activity("executing a query").object(ms.getId());
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    // 第一次查询，清空本地缓存
    if (queryStack == 0 && ms.isFlushCacheRequired()) {
      clearLocalCache();
    }
    List<E> list;
    try {
      queryStack++;
      // 查询缓存
      list = resultHandler == null ? (List<E>) localCache.getObject(key) : null;
      // 获取缓存的属性值，并设置到 parameter 中
      if (list != null) {
        handleLocallyCachedOutputParameters(ms, key, parameter, boundSql);
      }
      // 查询数据库
      else {
        list = queryFromDatabase(ms, parameter, rowBounds, resultHandler, key, boundSql);
      }
    } finally {
      queryStack--;
    }
    // 执行完成; 加载延迟加载的集合
    if (queryStack == 0) {
      for (DeferredLoad deferredLoad : deferredLoads) {
        deferredLoad.load();
      }
      // issue #601
      deferredLoads.clear();
      // STATEMENT, 清除缓存
      if (configuration.getLocalCacheScope() == LocalCacheScope.STATEMENT) {
        // issue #482
        clearLocalCache();
      }
    }
    return list;
  }

  /**
   * 游标查询
   * @param ms
   * @param parameter
   * @param rowBounds
   * @param <E>
   * @return
   * @throws SQLException
   */
  @Override
  public <E> Cursor<E> queryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds) throws SQLException {
    // sql
    BoundSql boundSql = ms.getBoundSql(parameter);
    // **子类实现**
    return doQueryCursor(ms, parameter, rowBounds, boundSql);
  }

  /**
   * 延迟加载
   * @param ms
   * @param resultObject
   * @param property
   * @param key
   * @param targetType
   */
  @Override
  public void deferLoad(MappedStatement ms, MetaObject resultObject, String property, CacheKey key, Class<?> targetType) {
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    // 延迟加载对象
    DeferredLoad deferredLoad = new DeferredLoad(resultObject, property, key, localCache, configuration, targetType);
    // 是否加载
    if (deferredLoad.canLoad()) {
      deferredLoad.load();
    }
    // 添加到延迟加载列表中
    else {
      deferredLoads.add(new DeferredLoad(resultObject, property, key, localCache, configuration, targetType));
    }
  }

  /**
   * 计算 CacheKey； 添加要计算的对象
   * id+offset+limit+sql+paramValue+env
   * @param ms
   * @param parameterObject
   * @param rowBounds
   * @param boundSql
   * @return
   */
  @Override
  public CacheKey createCacheKey(MappedStatement ms, Object parameterObject, RowBounds rowBounds, BoundSql boundSql) {
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    // 计算缓存key; 计算多个对象的 hashcode
    CacheKey cacheKey = new CacheKey();
    cacheKey.update(ms.getId());
    cacheKey.update(rowBounds.getOffset());
    cacheKey.update(rowBounds.getLimit());
    cacheKey.update(boundSql.getSql());
    // sql变量表达式 #{type}
    List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
    // 类型处理注册器
    TypeHandlerRegistry typeHandlerRegistry = ms.getConfiguration().getTypeHandlerRegistry();
    // mimic DefaultParameterHandler logic

    for (ParameterMapping parameterMapping : parameterMappings) {
      if (parameterMapping.getMode() != ParameterMode.OUT) {
        Object value;
        // sql变量表达式名称 #{type} -> type
        String propertyName = parameterMapping.getProperty();
        // 获得属性值
        if (boundSql.hasAdditionalParameter(propertyName)) {
          value = boundSql.getAdditionalParameter(propertyName);
        } else if (parameterObject == null) {
          value = null;
        } else if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
          value = parameterObject;
        } else {
          MetaObject metaObject = configuration.newMetaObject(parameterObject);
          value = metaObject.getValue(propertyName);
        }
        // 计算缓存key
        cacheKey.update(value);
      }
    }
    // 计算缓存key
    if (configuration.getEnvironment() != null) {
      // issue #176
      cacheKey.update(configuration.getEnvironment().getId());
    }
    return cacheKey;
  }

  /**
   * 是否已经缓存
   * @param ms
   * @param key
   * @return
   */
  @Override
  public boolean isCached(MappedStatement ms, CacheKey key) {
    return localCache.getObject(key) != null;
  }

  /**
   * 提交事务
   * @param required
   * @throws SQLException
   */
  @Override
  public void commit(boolean required) throws SQLException {
    if (closed) {
      throw new ExecutorException("Cannot commit, transaction is already closed");
    }
    // 清除一级缓存
    clearLocalCache();
    // 刷新
    flushStatements();
    if (required) {
      transaction.commit();
    }
  }

  /**
   * 回滚
   * @param required
   * @throws SQLException
   */
  @Override
  public void rollback(boolean required) throws SQLException {
    if (!closed) {
      try {
        // 清空本地缓存
        clearLocalCache();
        // 刷新 Statement ；*子类实现*
        flushStatements(true);
      } finally {
        if (required) {
          transaction.rollback();
        }
      }
    }
  }

  @Override
  public void clearLocalCache() {
    if (!closed) {
      localCache.clear();
      localOutputParameterCache.clear();
    }
  }

  protected abstract int doUpdate(MappedStatement ms, Object parameter)
      throws SQLException;

  protected abstract List<BatchResult> doFlushStatements(boolean isRollback)
      throws SQLException;

  protected abstract <E> List<E> doQuery(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql)
      throws SQLException;

  protected abstract <E> Cursor<E> doQueryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds, BoundSql boundSql)
      throws SQLException;

  protected void closeStatement(Statement statement) {
    if (statement != null) {
      try {
        statement.close();
      } catch (SQLException e) {
        // ignore
      }
    }
  }

  /**
   * Apply a transaction timeout.
   * @param statement a current statement
   * @throws SQLException if a database access error occurs, this method is called on a closed <code>Statement</code>
   * @since 3.4.0
   * @see StatementUtil#applyTransactionTimeout(Statement, Integer, Integer)
   */
  protected void applyTransactionTimeout(Statement statement) throws SQLException {
    StatementUtil.applyTransactionTimeout(statement, statement.getQueryTimeout(), transaction.getTimeout());
  }

  /**
   * 获取缓存的属性值，并设置到 parameter 中
   * @param ms
   * @param key
   * @param parameter
   * @param boundSql
   */
  private void handleLocallyCachedOutputParameters(MappedStatement ms, CacheKey key, Object parameter, BoundSql boundSql) {
    //
    if (ms.getStatementType() == StatementType.CALLABLE) {
      // 查询缓存
      final Object cachedParameter = localOutputParameterCache.getObject(key);
      if (cachedParameter != null && parameter != null) {
        // 创建 MetaObject
        final MetaObject metaCachedParameter = configuration.newMetaObject(cachedParameter);
        final MetaObject metaParameter = configuration.newMetaObject(parameter);
        // sql变量表达式
        for (ParameterMapping parameterMapping : boundSql.getParameterMappings()) {
          // 不是输入参数
          if (parameterMapping.getMode() != ParameterMode.IN) {
            // 获取缓存的属性值，并设置到 parameter 中
            final String parameterName = parameterMapping.getProperty();
            final Object cachedValue = metaCachedParameter.getValue(parameterName);
            metaParameter.setValue(parameterName, cachedValue);
          }
        }
      }
    }
  }

  private <E> List<E> queryFromDatabase(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, CacheKey key, BoundSql boundSql) throws SQLException {
    List<E> list;
    // 缓存一个空实现
    localCache.putObject(key, EXECUTION_PLACEHOLDER);
    try {
      // *子类实现*
      list = doQuery(ms, parameter, rowBounds, resultHandler, boundSql);
    } finally {
      localCache.removeObject(key);
    }
    // 缓存 query 结果
    localCache.putObject(key, list);
    // 回调 ？todo , 缓存参数
    if (ms.getStatementType() == StatementType.CALLABLE) {
      localOutputParameterCache.putObject(key, parameter);
    }
    return list;
  }

  /**
   *
   * @param statementLog
   * @return
   * @throws SQLException
   */
  protected Connection getConnection(Log statementLog) throws SQLException {
    Connection connection = transaction.getConnection();
    if (statementLog.isDebugEnabled()) {
      return ConnectionLogger.newInstance(connection, statementLog, queryStack);
    } else {
      return connection;
    }
  }

  @Override
  public void setExecutorWrapper(Executor wrapper) {
    this.wrapper = wrapper;
  }

  /**
   * 延迟加载对象； 已经执行完sql
   */
  private static class DeferredLoad {

    private final MetaObject resultObject;
    private final String property;
    private final Class<?> targetType;
    private final CacheKey key;
    private final PerpetualCache localCache;
    private final ObjectFactory objectFactory;
    private final ResultExtractor resultExtractor;

    // issue #781
    public DeferredLoad(MetaObject resultObject,
                        String property,
                        CacheKey key,
                        PerpetualCache localCache,
                        Configuration configuration,
                        Class<?> targetType) {
      this.resultObject = resultObject;
      this.property = property;
      this.key = key;
      this.localCache = localCache;
      this.objectFactory = configuration.getObjectFactory();
      this.resultExtractor = new ResultExtractor(configuration, objectFactory);
      this.targetType = targetType;
    }

    public boolean canLoad() {
      return localCache.getObject(key) != null && localCache.getObject(key) != EXECUTION_PLACEHOLDER;
    }

    public void load() {
      @SuppressWarnings("unchecked")
      // we suppose we get back a List
        // 缓存值
      List<Object> list = (List<Object>) localCache.getObject(key);
      // 转换结果类型
      Object value = resultExtractor.extractObjectFromList(list, targetType);
      // 保存
      resultObject.setValue(property, value);
    }

  }

}
