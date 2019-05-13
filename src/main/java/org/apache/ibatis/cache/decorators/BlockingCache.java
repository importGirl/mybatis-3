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
package org.apache.ibatis.cache.decorators;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.CacheException;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Simple blocking decorator
 *
 * 简单的阻塞Cache 实现类
 * 如果获取的缓存不存在，则会阻塞后续的其它线程去获取该缓存；
 * 避免线程A获取不到，添加值到缓存中时， B,C 也同样去添加值到缓存中；
 *
 * 实现原理：使用ReentrantLock 锁同步 获取缓存方法和设置缓存方法
 *
 * Simple and inefficient version of EhCache's BlockingCache decorator.
 * It sets a lock over a cache key when the element is not found in cache.
 * This way, other threads will wait until this element is filled instead of hitting the database.
 *
 * @author Eduardo Macarron
 *
 */
public class BlockingCache implements Cache {

  private long timeout;                                             // 超时时间
  private final Cache delegate;                                     // 装饰者 cache 类
  private final ConcurrentHashMap<Object, ReentrantLock> locks;     // 锁集合

  public BlockingCache(Cache delegate) {
    this.delegate = delegate;
    this.locks = new ConcurrentHashMap<>();
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

  @Override
  public void putObject(Object key, Object value) {
    try {
      delegate.putObject(key, value);
    } finally {
      releaseLock(key);
    }
  }

  /**
   * 获取缓存值
   * @param key The key
   * @return
   */
  @Override
  public Object getObject(Object key) {
    // 获取锁
    acquireLock(key);
    // 获取值
    Object value = delegate.getObject(key);
    // 释放锁
    if (value != null) {
      releaseLock(key);
    }
    return value;
  }

  @Override
  public Object removeObject(Object key) {
    // despite of its name, this method is called only to release locks
    releaseLock(key);
    return null;
  }

  @Override
  public void clear() {
    delegate.clear();
  }

  @Override
  public ReadWriteLock getReadWriteLock() {
    return null;
  }

  /**
   * 设置并获取锁
   * @param key
   * @return
   */
  private ReentrantLock getLockForKey(Object key) {
    return locks.computeIfAbsent(key, k -> new ReentrantLock());
  }

  /**
   * 获取锁
   * @param key
   */
  private void acquireLock(Object key) {
    // 获取锁
    Lock lock = getLockForKey(key);
    // 是否设置类超时时间
    if (timeout > 0) {
      try {
        // 调用超时时间到获取锁方法
        boolean acquired = lock.tryLock(timeout, TimeUnit.MILLISECONDS);
        if (!acquired) {
          throw new CacheException("Couldn't get a lock in " + timeout + " for the key " +  key + " at the cache " + delegate.getId());
        }
      } catch (InterruptedException e) {
        throw new CacheException("Got interrupted while trying to acquire lock for key " + key, e);
      }
    } else {
      // 获取锁
      lock.lock();
    }
  }

  /**
   * 释放锁
   * @param key
   */
  private void releaseLock(Object key) {
    ReentrantLock lock = locks.get(key);
    // 查询当前线程是否保持此锁
    if (lock.isHeldByCurrentThread()) {
      // 释放锁
      lock.unlock();
    }
  }

  public long getTimeout() {
    return timeout;
  }

  public void setTimeout(long timeout) {
    this.timeout = timeout;
  }
}
