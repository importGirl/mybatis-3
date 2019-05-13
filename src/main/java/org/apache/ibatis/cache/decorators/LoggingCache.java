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
package org.apache.ibatis.cache.decorators;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

import java.util.concurrent.locks.ReadWriteLock;

/**
 * 支持日志的Cache 类
 * @author Clinton Begin
 */
public class LoggingCache implements Cache {

  private final Log log;          // 日志对象
  private final Cache delegate;   // 装饰的 cache 对象
  protected int requests = 0;     // 统计请求缓存的次数
  protected int hits = 0;         // 统计命中缓存的次数

  public LoggingCache(Cache delegate) {
    this.delegate = delegate;
    // 获取相同的日志 log 对象
    this.log = LogFactory.getLog(getId());
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
  public void putObject(Object key, Object object) {
    delegate.putObject(key, object);
  }

  /**
   * 获取缓存对象
   * @param key The key
   * @return
   */
  @Override
  public Object getObject(Object key) {
    // 添加统计次数
    requests++;
    // 获取装饰 cache 对象的值
    final Object value = delegate.getObject(key);
    // 统计命中次数
    if (value != null) {
      hits++;
    }
    if (log.isDebugEnabled()) {
      log.debug("Cache Hit Ratio [" + getId() + "]: " + getHitRatio());
    }
    // 返回
    return value;
  }

  @Override
  public Object removeObject(Object key) {
    return delegate.removeObject(key);
  }

  @Override
  public void clear() {
    delegate.clear();
  }

  @Override
  public ReadWriteLock getReadWriteLock() {
    return null;
  }

  @Override
  public int hashCode() {
    return delegate.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return delegate.equals(obj);
  }

  /**
   * 缓存命中率
   * @return
   */
  private double getHitRatio() {
    return (double) hits / (double) requests;
  }

}
