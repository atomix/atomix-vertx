/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package io.atomix.vertx;

import io.atomix.catalyst.util.Assert;
import io.atomix.collections.DistributedMap;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.shareddata.AsyncMap;

import java.time.Duration;

/**
 * Atomix async map.
 *
 * @author <a href="http://github.com/kuujo>Jordan Halterman</a>
 */
public class AtomixAsyncMap<K, V> implements AsyncMap<K, V> {
  private final Vertx vertx;
  private final DistributedMap<K, V> map;

  AtomixAsyncMap(Vertx vertx, DistributedMap<K, V> map) {
    this.vertx = Assert.notNull(vertx, "vertx");
    this.map = Assert.notNull(map, "map");
  }

  @Override
  public void get(K k, Handler<AsyncResult<V>> handler) {
    map.get(k).whenComplete(VertxFutures.resultHandler(handler, vertx.getOrCreateContext()));
  }

  @Override
  public void put(K k, V v, Handler<AsyncResult<Void>> handler) {
    map.put(k, v).whenComplete(VertxFutures.voidHandler(handler, vertx.getOrCreateContext()));
  }

  @Override
  public void put(K k, V v, long l, Handler<AsyncResult<Void>> handler) {
    map.put(k, v, Duration.ofMillis(l)).whenComplete(VertxFutures.voidHandler(handler, vertx.getOrCreateContext()));
  }

  @Override
  public void putIfAbsent(K k, V v, Handler<AsyncResult<V>> handler) {
    map.putIfAbsent(k, v).whenComplete(VertxFutures.resultHandler(handler, vertx.getOrCreateContext()));
  }

  @Override
  public void putIfAbsent(K k, V v, long l, Handler<AsyncResult<V>> handler) {
    map.putIfAbsent(k, v, Duration.ofMillis(l)).whenComplete(VertxFutures.resultHandler(handler, vertx.getOrCreateContext()));
  }

  @Override
  public void remove(K k, Handler<AsyncResult<V>> handler) {
    map.remove(k).whenComplete(VertxFutures.resultHandler(handler, vertx.getOrCreateContext()));
  }

  @Override
  public void removeIfPresent(K k, V v, Handler<AsyncResult<Boolean>> handler) {
    map.remove(k, v).whenComplete(VertxFutures.resultHandler(handler, vertx.getOrCreateContext()));
  }

  @Override
  public void replace(K k, V v, Handler<AsyncResult<V>> handler) {
    map.replace(k, v).whenComplete(VertxFutures.resultHandler(handler, vertx.getOrCreateContext()));
  }

  @Override
  public void replaceIfPresent(K k, V oldValue, V newValue, Handler<AsyncResult<Boolean>> handler) {
    map.replace(k, oldValue, newValue).whenComplete(VertxFutures.resultHandler(handler, vertx.getOrCreateContext()));
  }

  @Override
  public void clear(Handler<AsyncResult<Void>> handler) {
    map.clear().whenComplete(VertxFutures.voidHandler(handler, vertx.getOrCreateContext()));
  }

  @Override
  public void size(Handler<AsyncResult<Integer>> handler) {
    map.size().whenComplete(VertxFutures.resultHandler(handler, vertx.getOrCreateContext()));
  }

}
