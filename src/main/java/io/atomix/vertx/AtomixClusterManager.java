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
import io.atomix.AtomixReplica;
import io.atomix.catalyst.util.Assert;
import io.atomix.catalyst.util.concurrent.SingleThreadContext;
import io.atomix.catalyst.util.concurrent.ThreadContext;
import io.atomix.collections.DistributedMap;
import io.atomix.collections.DistributedMultiMap;
import io.atomix.coordination.DistributedGroup;
import io.atomix.coordination.GroupMember;
import io.atomix.coordination.LocalGroupMember;
import io.vertx.core.*;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.Counter;
import io.vertx.core.shareddata.Lock;
import io.vertx.core.shareddata.impl.ClusterSerializable;
import io.vertx.core.spi.cluster.AsyncMultiMap;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.cluster.NodeListener;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

/**
 * Vert.x Atomix cluster manager.
 *
 * @author <a href="http://github.com/kuujo>Jordan Halterman</a>
 */
public class AtomixClusterManager implements ClusterManager {
  private static final String DEFAULT_PROPERTIES_FILE = "atomix.properties";
  private final Atomix atomix;
  private final ThreadContext context;
  private DistributedGroup group;
  private NodeListener listener;
  private volatile LocalGroupMember member;
  private volatile boolean active;
  private Vertx vertx;

  /**
   * Loads properties from a properties file.
   */
  private static Properties loadProperties(String propertiesFile) {
    Properties properties = new Properties();
    try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(propertiesFile)) {
      properties.load(is);
    } catch (IOException e) {
      throw new RuntimeException("failed to load default transport properties", e);
    }
    return properties;
  }

  public AtomixClusterManager() {
    this(DEFAULT_PROPERTIES_FILE);
  }

  public AtomixClusterManager(String propertiesFile) {
    this(loadProperties(propertiesFile));
  }

  public AtomixClusterManager(Properties properties) {
    this(new AtomixReplica(properties));
  }

  public AtomixClusterManager(Atomix atomix) {
    this.atomix = Assert.notNull(atomix, "atomix");
    this.context = new SingleThreadContext("atomix-vertx-%d", atomix.serializer());
    atomix.serializer().registerDefault(ClusterSerializable.class, ClusterSerializableSerializer.class);
  }

  /**
   * Returns the underlying Atomix instance.
   *
   * @return The underlying Atomix instance.
   */
  public Atomix atomix() {
    return atomix;
  }

  @Override
  public void setVertx(Vertx vertx) {
    this.vertx = vertx;
  }

  @Override
  public <K, V> void getAsyncMultiMap(String name, Handler<AsyncResult<AsyncMultiMap<K, V>>> handler) {
    atomix.<K, V>getMultiMap(name).whenComplete(VertxFutures.<DistributedMultiMap<K, V>, AsyncMultiMap<K, V>>convertHandler(handler, map -> new AtomixAsyncMultiMap<K, V>(vertx, map), vertx.getOrCreateContext()));
  }

  @Override
  public <K, V> void getAsyncMap(String name, Handler<AsyncResult<AsyncMap<K, V>>> handler) {
    atomix.<K, V>getMap(name).whenComplete(VertxFutures.<DistributedMap<K, V>, AsyncMap<K, V>>convertHandler(handler, map -> new AtomixAsyncMap<K, V>(vertx, map), vertx.getOrCreateContext()));
  }

  @Override
  public <K, V> Map<K, V> getSyncMap(String name) {
    try {
      return new AtomixMap<>(vertx, atomix.<K, V>getMap(name).get());
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void getLockWithTimeout(String name, long timeout, Handler<AsyncResult<Lock>> handler) {
    Context context = vertx.getOrCreateContext();
    atomix.getLock(name).whenComplete((lock, error) -> {
      if (error == null) {
        lock.tryLock(Duration.ofMillis(timeout)).whenComplete((lockResult, lockError) -> {
          if (lockError == null) {
            if (lockResult == null) {
              context.runOnContext(v -> Future.<Lock>failedFuture(new VertxException("Timed out waiting to get lock " + name)).setHandler(handler));
            } else {
              context.runOnContext(v -> Future.<Lock>succeededFuture(new AtomixLock(vertx, lock)).setHandler(handler));
            }
          } else {
            context.runOnContext(v -> Future.<Lock>failedFuture(lockError).setHandler(handler));
          }
        });
      } else {
        context.runOnContext(v -> Future.<Lock>failedFuture(error).setHandler(handler));
      }
    });
  }

  @Override
  public void getCounter(String name, Handler<AsyncResult<Counter>> handler) {
    atomix.getLong(name).whenComplete(VertxFutures.convertHandler(handler, counter -> new AtomixCounter(vertx, counter), vertx.getOrCreateContext()));
  }

  @Override
  public String getNodeID() {
    return member.id();
  }

  @Override
  public List<String> getNodes() {
    List<String> nodes = new ArrayList<>();
    for (GroupMember member : group.members()) {
      nodes.add(member.id());
    }
    return nodes;
  }

  @Override
  public void nodeListener(NodeListener nodeListener) {
    this.listener = nodeListener;
  }

  @Override
  public void join(Handler<AsyncResult<Void>> handler) {
    Context context = vertx.getOrCreateContext();
    active = true;
    atomix.open().whenComplete((openResult, openError) -> {
      if (openError == null) {
        atomix.getGroup("__atomix__").whenComplete((group, groupError) -> {
          if (groupError == null) {
            this.group = group;
            group.join().whenComplete((member, joinError) -> {
              if (joinError == null) {
                this.member = member;
                group.onJoin(this::handleJoin);
                group.onLeave(this::handleLeave);
                context.runOnContext(v -> Future.<Void>succeededFuture().setHandler(handler));
              } else {
                context.runOnContext(v -> Future.<Void>failedFuture(joinError).setHandler(handler));
              }
            });
          } else {
            context.runOnContext(v -> Future.<Void>failedFuture(groupError).setHandler(handler));
          }
        });
      } else {
        context.runOnContext(v -> Future.<Void>failedFuture(openError).setHandler(handler));
      }
    });
  }

  /**
   * Handles a member joining.
   */
  private void handleJoin(GroupMember member) {
    if (listener != null) {
      context.executor().execute(() -> {
        if (active) {
          listener.nodeAdded(member.id());
        }
      });
    }
  }

  /**
   * Handles a member leaving.
   */
  private void handleLeave(GroupMember member) {
    if (listener != null) {
      context.executor().execute(() -> {
        if (active) {
          listener.nodeLeft(member.id());
        }
      });
    }
  }

  @Override
  public void leave(Handler<AsyncResult<Void>> handler) {
    Context context = vertx.getOrCreateContext();
    if (member != null) {
      active = false;
      member.leave().whenComplete((leaveResult, leaveError) -> {
        if (leaveError == null) {
          group = null;
          member = null;
          atomix.close().whenComplete(VertxFutures.voidHandler(handler, context));
        } else {
          context.runOnContext(v -> Future.<Void>failedFuture(leaveError).setHandler(handler));
        }
      });
    } else {
      context.runOnContext(v -> Future.<Void>succeededFuture().setHandler(handler));
    }
  }

  @Override
  public boolean isActive() {
    return active;
  }

}
