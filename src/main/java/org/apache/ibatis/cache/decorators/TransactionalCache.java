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
package org.apache.ibatis.cache.decorators;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;

/**
 * The 2nd level cache transactional buffer.
 * <p>
 * This class holds all cache entries that are to be added to the 2nd level cache during a Session.
 * Entries are sent to the cache when commit is called or discarded if the Session is rolled back.
 * Blocking cache support has been added. Therefore any get() that returns a cache miss
 * will be followed by a put() so any lock associated with the key can be released.
 *
 * 事务缓存实现类 TODO 如何使用？
 * 实现原理：Map 缓存提交的事务对象；Set 缓存未命中的缓存
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class TransactionalCache implements Cache {

  private static final Log log = LogFactory.getLog(TransactionalCache.class);

  // ms.getCache() -二级缓存 ； 委托给cache， 进行 事务结果 都缓存缓存操作
  private final Cache delegate;
  private boolean clearOnCommit;                          // 是否提交事务后清除缓存
  private final Map<Object, Object> entriesToAddOnCommit; // 提交的事务对象的集合
  private final Set<Object> entriesMissedInCache;         // 没有命中缓存的集合

  public TransactionalCache(Cache delegate) {
    this.delegate = delegate;
    this.clearOnCommit = false;
    this.entriesToAddOnCommit = new HashMap<>();
    this.entriesMissedInCache = new HashSet<>();
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

  /**
   *
   * @param key The key
   * @return
   */
  @Override
  public Object getObject(Object key) {
    // issue #116
    Object object = delegate.getObject(key);
    // 添加到未命中集合中
    if (object == null) {
      entriesMissedInCache.add(key);
    }

    // issue #146
    if (clearOnCommit) {
      return null;
    } else {
      return object;
    }
  }

  @Override
  public ReadWriteLock getReadWriteLock() {
    return null;
  }

  @Override
  public void putObject(Object key, Object object) {
    entriesToAddOnCommit.put(key, object);
  }

  @Override
  public Object removeObject(Object key) {
    return null;
  }

  @Override
  public void clear() {
    clearOnCommit = true;
    entriesToAddOnCommit.clear();
  }

  public void commit() {
    // 是否清空
    if (clearOnCommit) {
      delegate.clear();
    }
    // 刷新需要缓存的事务到缓存中
    flushPendingEntries();
    // 重置集合
    reset();
  }

  /**
   * 回滚
   */
  public void rollback() {
    // 释放未命中缓存
    unlockMissedEntries();
    // 重置； 但是 delegate 依然存在
    reset();
  }

  private void reset() {
    clearOnCommit = false;
    entriesToAddOnCommit.clear();
    entriesMissedInCache.clear();
  }

  /**
   * 缓存事务结果到二级缓存中
   * 刷新需要缓存的事务到缓存中
   */
  private void flushPendingEntries() {
    // 需要提交的事务集合
    for (Map.Entry<Object, Object> entry : entriesToAddOnCommit.entrySet()) {
      delegate.putObject(entry.getKey(), entry.getValue());
    }
    // 未命中缓存到集合， value 设置为null
    for (Object entry : entriesMissedInCache) {
      if (!entriesToAddOnCommit.containsKey(entry)) {
        delegate.putObject(entry, null);
      }
    }
  }

  /**
   * 是否未命中的缓存
   */
  private void unlockMissedEntries() {
    for (Object entry : entriesMissedInCache) {
      try {
        delegate.removeObject(entry);
      } catch (Exception e) {
        log.warn("Unexpected exception while notifiying a rollback to the cache adapter."
            + "Consider upgrading your cache adapter to the latest version.  Cause: " + e);
      }
    }
  }

}
