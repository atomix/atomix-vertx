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
import io.atomix.Quorum;
import io.atomix.catalyst.transport.Address;
import io.atomix.catalyst.transport.LocalServerRegistry;
import io.atomix.catalyst.transport.LocalTransport;
import io.atomix.copycat.server.storage.Storage;
import io.atomix.copycat.server.storage.StorageLevel;

import java.util.Collections;
import java.util.List;

/**
 * Vert.x test helper.
 *
 * @author <a href="http://github.com/kuujo>Jordan Halterman</a>
 */
final class AtomixVertxTestHelper {
  private int port = 5000;
  private List<Address> members;
  private LocalServerRegistry registry = new LocalServerRegistry();

  /**
   * Returns the address for the next cluster member.
   */
  private Address nextAddress() {
    Address address = new Address("localhost", port++);
    if (members == null) {
      members = Collections.singletonList(address);
    }
    return address;
  }

  /**
   * Returns the next Atomix cluster manager.
   *
   * @return The next Atomix cluster manager.
   */
  AtomixClusterManager createClusterManager() {
    Atomix replica = AtomixReplica.builder(nextAddress(), members)
      .withTransport(new LocalTransport(registry))
      .withStorage(new Storage(StorageLevel.MEMORY))
      .withQuorumHint(Quorum.ALL)
      .build();
    replica.serializer().disableWhitelist();
    return new AtomixClusterManager(replica);
  }

}
