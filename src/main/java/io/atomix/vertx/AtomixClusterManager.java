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

import io.atomix.Atomix;
import io.atomix.atomic.DistributedAtomicLong;
import io.atomix.catalyst.util.Assert;
import io.atomix.collections.DistributedMap;
import io.atomix.collections.DistributedMultiMap;
import io.atomix.coordination.DistributedLock;
import io.atomix.coordination.DistributedMembershipGroup;
import io.atomix.coordination.GroupMember;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.Counter;
import io.vertx.core.shareddata.Lock;
import io.vertx.core.spi.cluster.AsyncMultiMap;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.cluster.NodeListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Vert.x Atomix cluster manager.
 *
 * @author <a href="http://github.com/kuujo>Jordan Halterman</a>
 */
public class AtomixClusterManager implements ClusterManager {
  private final Atomix atomix;
  private DistributedMembershipGroup group;
  private NodeListener listener;
  private Vertx vertx;
  private boolean active;

  public AtomixClusterManager(Atomix atomix) {
    this.atomix = Assert.notNull(atomix, "atomix");
  }

  @Override
  public void setVertx(Vertx vertx) {
    this.vertx = vertx;
  }

  @Override
  public <K, V> void getAsyncMultiMap(String name, Handler<AsyncResult<AsyncMultiMap<K, V>>> handler) {
    atomix.<DistributedMultiMap<K, V>>get(name, DistributedMultiMap.class)
      .whenComplete(VertxFutures.<DistributedMultiMap<K, V>, AsyncMultiMap<K, V>>convertHandler(handler, AtomixAsyncMultiMap::new));
  }

  @Override
  public <K, V> void getAsyncMap(String name, Handler<AsyncResult<AsyncMap<K, V>>> handler) {
    atomix.<DistributedMap<K, V>>get(name, DistributedMap.class)
      .whenComplete(VertxFutures.<DistributedMap<K, V>, AsyncMap<K, V>>convertHandler(handler, AtomixAsyncMap::new));
  }

  @Override
  public <K, V> Map<K, V> getSyncMap(String name) {
    try {
      return new AtomixMap<>(atomix.<DistributedMap<K, V>>get(name, DistributedMap.class).get());
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void getLockWithTimeout(String name, long l, Handler<AsyncResult<Lock>> handler) {
    atomix.get(name, DistributedLock.class).whenComplete((lock, error) -> {
      if (error == null) {
        lock.lock().whenComplete(VertxFutures.<Void, Lock>convertHandler(handler, v -> new AtomixLock(lock)));
      } else {
        Future.<Lock>failedFuture(error).setHandler(handler);
      }
    });
  }

  @Override
  public void getCounter(String name, Handler<AsyncResult<Counter>> handler) {
    atomix.get(name, DistributedAtomicLong.class).whenComplete(VertxFutures.convertHandler(handler, AtomixCounter::new));
  }

  @Override
  public String getNodeID() {
    return "" + group.member().id();
  }

  @Override
  public List<String> getNodes() {
    List<String> nodes = new ArrayList<>();
    for (GroupMember member : group.members()) {
      nodes.add("" + member.id());
    }
    return nodes;
  }

  @Override
  public void nodeListener(NodeListener nodeListener) {
    this.listener = nodeListener;
  }

  @Override
  public void join(Handler<AsyncResult<Void>> handler) {
    atomix.open().whenComplete((openResult, openError) -> {
      if (openError == null) {
        atomix.get("__vertx", DistributedMembershipGroup.class).whenComplete((groupResult, groupError) -> {
          if (groupError == null) {
            this.group = groupResult;
            group.join().whenComplete((joinResult, joinError) -> {
              if (joinError == null) {
                active = true;
                group.onJoin(this::handleJoin);
                group.onLeave(this::handleLeave);
                Future.<Void>succeededFuture().setHandler(handler);
              } else {
                Future.<Void>failedFuture(joinError).setHandler(handler);
              }
            });
          } else {
            Future.<Void>failedFuture(groupError).setHandler(handler);
          }
        });
      } else {
        Future.<Void>failedFuture(openError).setHandler(handler);
      }
    });
  }

  /**
   * Handles a member joining.
   */
  private void handleJoin(GroupMember member) {
    if (listener != null) {
      listener.nodeAdded("" + member.id());
    }
  }

  /**
   * Handles a member leaving.
   */
  private void handleLeave(GroupMember member) {
    if (listener != null) {
      listener.nodeLeft("" + member.id());
    }
  }

  @Override
  public void leave(Handler<AsyncResult<Void>> handler) {
    if (group != null) {
      active = false;
      group.leave().whenComplete((leaveResult, leaveError) -> {
        if (leaveError == null) {
          group = null;
          atomix.close().whenComplete(VertxFutures.voidHandler(handler));
        } else {
          Future.<Void>failedFuture(leaveError).setHandler(handler);
        }
      });
    }
  }

  @Override
  public boolean isActive() {
    return active;
  }

}
