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
import io.atomix.coordination.DistributedLock;
import io.vertx.core.Vertx;
import io.vertx.core.shareddata.Lock;

import java.util.concurrent.ExecutionException;

/**
 * Atomix distributed lock.
 *
 * @author <a href="http://github.com/kuujo>Jordan Halterman</a>
 */
public class AtomixLock implements Lock {
  private final DistributedLock lock;

  public AtomixLock(Vertx vertx, DistributedLock lock) {
    this.lock = Assert.notNull(lock, "lock");
  }

  @Override
  public void release() {
    try {
      lock.unlock().get();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

}
