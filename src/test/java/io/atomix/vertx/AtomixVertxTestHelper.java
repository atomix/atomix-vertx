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

import io.atomix.AtomixClient;
import io.atomix.AtomixReplica;
import io.atomix.catalyst.transport.Address;
import io.atomix.catalyst.transport.local.LocalServerRegistry;
import io.atomix.catalyst.transport.local.LocalTransport;
import io.atomix.copycat.server.storage.Storage;
import io.atomix.copycat.server.storage.StorageLevel;
import io.vertx.core.shareddata.impl.ClusterSerializable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Vert.x test helper.
 *
 * @author <a href="http://github.com/kuujo>Jordan Halterman</a>
 */
final class AtomixVertxTestHelper {
  private final LocalServerRegistry registry = new LocalServerRegistry();
  private final List<AtomixReplica> replicas = new ArrayList<>();
  private final List<Address> members = Arrays.asList(
    new Address("localhost", 5000),
    new Address("localhost", 5001),
    new Address("localhost", 5002)
  );

  public void setUp() throws Exception {
    CompletableFuture[] futures = new CompletableFuture[members.size()];
    for (int i = 0; i < members.size(); i++) {
      AtomixReplica replica = AtomixReplica.builder(members.get(i))
        .withTransport(new LocalTransport(registry))
        .withStorage(new Storage(StorageLevel.MEMORY))
        .withElectionTimeout(Duration.ofMillis(500))
        .withHeartbeatInterval(Duration.ofMillis(200))
        .withSessionTimeout(Duration.ofSeconds(10))
        .build();
      replica.serializer().registerDefault(ClusterSerializable.class, ClusterSerializableSerializer.class);
      replicas.add(replica);
      futures[i] = replica.bootstrap(members);
    }
    CompletableFuture.allOf(futures).get(30, TimeUnit.SECONDS);
  }

  /**
   * Returns the next Atomix cluster manager.
   *
   * @return The next Atomix cluster manager.
   */
  AtomixClusterManager createClusterManager() {
    AtomixClient client = AtomixClient.builder()
      .withTransport(new LocalTransport(registry))
      .build();
    try {
      client.connect(members).get(30, TimeUnit.SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
    return new AtomixClusterManager(client);
  }

  public void tearDown() {
    CompletableFuture[] futures = new CompletableFuture[replicas.size()];
    for (int i = 0; i < replicas.size(); i++) {
      futures[i] = replicas.get(i).leave();
    }

    try {
      CompletableFuture.allOf(futures).get(30, TimeUnit.SECONDS);
    } catch (Exception e) {
    }
  }

}
