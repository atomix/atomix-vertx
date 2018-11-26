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

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.atomix.cluster.Node;
import io.atomix.cluster.discovery.BootstrapDiscoveryProvider;
import io.atomix.cluster.protocol.SwimMembershipProtocol;
import io.atomix.core.Atomix;
import io.atomix.protocols.raft.partition.RaftPartitionGroup;
import io.atomix.utils.net.Address;

/**
 * Vert.x test helper.
 *
 * @author <a href="http://github.com/kuujo>Jordan Halterman</a>
 */
final class AtomixVertxTestHelper {
  private static final int BASE_PORT = 5000;
  private List<Atomix> instances;
  private int id = 10;

  void setUp() throws Exception {
    deleteData();
    instances = new ArrayList<>();
    instances.add(createAtomix(1, 1, 2, 3));
    instances.add(createAtomix(2, 1, 2, 3));
    instances.add(createAtomix(3, 1, 2, 3));
    List<CompletableFuture<Void>> futures = instances.stream().map(Atomix::start).collect(Collectors.toList());
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(30, TimeUnit.SECONDS);
  }

  /**
   * Creates an Atomix instance.
   */
  private Atomix createAtomix(int id, int... ids) {
    Collection<Node> nodes = IntStream.of(ids)
        .mapToObj(memberId -> Node.builder()
            .withId(String.valueOf(memberId))
            .withAddress(Address.from("localhost", BASE_PORT + memberId))
            .build())
        .collect(Collectors.toList());

    return Atomix.builder()
        .withClusterId("test")
        .withMemberId(String.valueOf(id))
        .withHost("localhost")
        .withPort(BASE_PORT + id)
        .withMembershipProtocol(SwimMembershipProtocol.builder()
            .withBroadcastDisputes(true)
            .withBroadcastUpdates(true)
            .withProbeInterval(Duration.ofMillis(100))
            .withNotifySuspect(true)
            .withFailureTimeout(Duration.ofSeconds(3))
            .build())
        .withMembershipProvider(new BootstrapDiscoveryProvider(nodes))
        .withManagementGroup(RaftPartitionGroup.builder("system")
            .withNumPartitions(1)
            .withPartitionSize(ids.length)
            .withMembers(nodes.stream().map(node -> node.id().id()).collect(Collectors.toSet()))
            .withDataDirectory(new File("target/test-logs/" + id + "/system"))
            .build())
        .withPartitionGroups(RaftPartitionGroup.builder("test")
            .withNumPartitions(3)
            .withPartitionSize(ids.length)
            .withMembers(nodes.stream().map(node -> node.id().id()).collect(Collectors.toSet()))
            .withDataDirectory(new File("target/test-logs/" + id + "/test"))
            .build())
        .build();
  }

  /**
   * Returns the next Atomix cluster manager.
   *
   * @return The next Atomix cluster manager.
   */
  AtomixClusterManager createClusterManager() {
    Atomix instance = createAtomix(id++, 1, 2, 3);
    instance.start().join();
    instances.add(instance);
    return new AtomixClusterManager(instance);
  }

  void tearDown() throws Exception {
    List<CompletableFuture<Void>> futures = instances.stream().map(Atomix::stop).collect(Collectors.toList());
    try {
      CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()])).join();
    } catch (Exception e) {
      // Do nothing
    }
    deleteData();
  }

  /**
   * Deletes data from the test data directory.
   */
  private static void deleteData() throws Exception {
    Path directory = Paths.get("target/test-logs/");
    if (Files.exists(directory)) {
      Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          Files.delete(file);
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
          Files.delete(dir);
          return FileVisitResult.CONTINUE;
        }
      });
    }
  }
}
