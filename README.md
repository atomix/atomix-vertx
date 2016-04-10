# Atomix Vert.x Cluster Manager

[![Build Status](https://travis-ci.org/atomix/atomix-vertx.svg)](https://travis-ci.org/atomix/atomix-vertx)

This project provides a cluster manager for [Vert.x](http://vertx.io) built on the [Atomix](http://atomix.io) distributed coordination framework.

### Installation

Add the Maven dependency to your `pom.xml`:

```xml
<dependency>
  <groupId>io.atomix.vertx</groupId>
  <artifactId>atomix-vertx</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Usage

To use the Atomix cluster manager, simply add the Atomix jar to your classpath. By default, when the Atomix
cluster manager is loaded it will load the Atomix configuration from an `atomix.properties` file on the
classpath. An example configuration file can be found at
[src/main/examples/atomix.properties](http://github.com/atomix/atomix-vertx/tree/master/src/main/examples/atomix.properties).
When used via this method, all nodes are `AtomixReplica` instances and are thus stateful.

To use the `AtomixClusterManager` programmatically, construct an `Atomix` instance via either `AtomixClient` or `AtomixReplica`.

## Configuring the cluster manager

The Atomix cluster manager can be configured in several ways. First, an `AtomixClient` that's connected to an external
Atomix cluster can be passed to the `AtomixClusterManager` to manage the Vert.x cluster:

```java
// Build an Atomix client
AtomixClient client = AtomixClient.builder()
  .withTransport(NettyTransport.builder()
    .withThreads(4)
    .build())
  .build();

// Register the Vert.x ClusterSerializable interface
client.serializer().registerDefault(ClusterSerializable.class, ClusterSerializableSerializer.class);

// Create a list of servers to which to connect
Collection<Address> cluster = Arrays.asList(
  new Address("123.456.789.0", 8700),
  new Address("123.456.789.1", 8700),
  new Address("123.456.789.2", 8700)
);

// Connect the client to the cluster
client.connect(cluster).join();

// Construct a Vert.x AtomixClusterManager
ClusterManager clusterManager = new AtomixClusterManager(client);

// Configure the Vert.x cluster manager and create a new clustered Vert.x instance
VertxOptions options = new VertxOptions().setClusterManager(manager);
Vertx.clusteredVertx(options, res -> {
  if (res.succeeded()) {
    Vertx vertx = res.result();
  } else {
    // failed!
  }
});
```

Note that when constructing an `AtomixClient` or `AtomixReplica` for use with Vert.x, you should register the
`ClusterSerializable` default serializer to ensure Vert.x compatible objects can be written to the cluster.

## Embedding an Atomix cluster

Alternatively, Atomix can be embedded within the Vert.x cluster by bootstrapping a new Atomix cluster. To bootstrap
a cluster, create a set of replicas (typically 3 or 5) on separate machines:

```java
AtomixReplica replica1 = AtomixReplica.builder(new Address("123.456.789.0", 8700))
  .withTransport(new NettyTransport())
  .withStorage(Storage.builder()
    .withDirectory(new File("logs/replica1"))
    .withStorageLevel(StorageLevel.MAPPED)
    .build())
  .build();
replica1.serializer().registerDefault(ClusterSerializable.class, ClusterSerializableSerializer.class);
```

```java
AtomixReplica replica2 = AtomixReplica.builder(new Address("123.456.789.1", 8700))
  .withTransport(new NettyTransport())
  .withStorage(Storage.builder()
    .withDirectory(new File("logs/replica2"))
    .withStorageLevel(StorageLevel.MAPPED)
    .build())
  .build();
replica2.serializer().registerDefault(ClusterSerializable.class, ClusterSerializableSerializer.class);
```

```java
AtomixReplica replica3 = AtomixReplica.builder(new Address("123.456.789.2", 8700))
  .withTransport(new NettyTransport())
  .withStorage(Storage.builder()
    .withDirectory(new File("logs/replica3"))
    .withStorageLevel(StorageLevel.MAPPED)
    .build())
  .build();
replica3.serializer().registerDefault(ClusterSerializable.class, ClusterSerializableSerializer.class);
```

Once a cluster of replicas have been created, `bootstrap` the cluster, passing the list of replicas to the
`bootstrap` method:

```java
// Bootstrap the Atomix cluster
Collection<Address> cluster = Arrays.asList(
  new Address("123.456.789.0", 8700),
  new Address("123.456.789.1", 8700),
  new Address("123.456.789.2", 8700)
);

// On machine 1
replica1.bootstrap(cluster).thenRun(() -> {
  VertxOptions options = new VertxOptions().setClusterManager(new AtomixClusterManager(replica1));
  Vertx.clusteredVertx(options, res -> {
    if (res.succeeded()) {
      Vertx vertx1 = res.result();
    } else {
      // failed!
    }
  });
});

// On machine 2
replica2.bootstrap(cluster).thenRun(() -> {
  VertxOptions options = new VertxOptions().setClusterManager(new AtomixClusterManager(replica2));
  Vertx.clusteredVertx(options, res -> {
    if (res.succeeded()) {
      Vertx vertx1 = res.result();
    } else {
      // failed!
    }
  });
});

// On machine 3
replica3.bootstrap(cluster).thenRun(() -> {
  VertxOptions options = new VertxOptions().setClusterManager(new AtomixClusterManager(replica3));
  Vertx.clusteredVertx(options, res -> {
    if (res.succeeded()) {
      Vertx vertx3 = res.result();
    } else {
      // failed!
    }
  });
});
```

## Adding nodes to an existing embedded Atomix cluster

Once the initial cluster has been bootstrapped, additional replicas can be `join`ed to the cluster:

```java
AtomixReplica replica = AtomixReplica.builder(new Address("123.456.789.3", 8700))
  .withTransport(new NettyTransport())
  .withStorage(new Storage(StorageLevel.MEMORY))
  .build();

Collection<Address> cluster = Arrays.asList(
  new Address("123.456.789.0", 8700),
  new Address("123.456.789.1", 8700),
  new Address("123.456.789.2", 8700)
);

// Join the Atomix cluster
replica.join(cluster).thenRun(() -> {
  // Create an Atomix cluster manager and start a new clustered Vertx instance
  VertxOptions options = new VertxOptions().setClusterManager(new AtomixClusterManager(replica));
  Vertx.clusteredVertx(options, res -> {
    if (res.succeeded()) {
      Vertx vertx = res.result();
    } else {
      // failed!
    }
  });
});
```

## Configuring replica types

The examples above add full voting `ACTIVE` Raft members to the Atomix cluster. However, adding Raft voting members
affects the size of the quorum and thus can impact write performance. Atomix supports adding stateful replicas to the
cluster that participate in replication through an asynchronous gossip protocol. These are known as `PASSIVE` replicas.
To add a `PASSIVE` node to the cluster, simply set the replica type to `AtomixReplica.Type.PASSIVE` when configuring the
replica:

```java
AtomixReplica replica = AtomixReplica.builder(new Address("123.456.789.3", 8700))
  .withType(AtomixReplica.Type.PASSIVE)
  .withTransport(new NettyTransport())
  .withStorage(new Storage(StorageLevel.MEMORY))
  .build();

replica.join(cluster).thenRun(() -> {
  VertxOptions options = new VertxOptions().setClusterManager(new AtomixClusterManager(replica));
  Vertx.clusteredVertx(options, res -> {
    if (res.succeeded()) {
      Vertx vertx = res.result();
    } else {
      // failed!
    }
  });
});
```

Note that Atomix clusters should typically have 3 or 5 `ACTIVE` nodes.

## Automatic Cluster Management

Finally, managing replica types in Atomix can be tedious. Because of the constraints of Raft, users are responsible for
configuring and managing the node types. However, Atomix does support automatic balancing of clusters. To configure a
cluster for automatic balancing of Raft replicas, use the `BalancingClusterManager` on each replica in the cluster:

```java
// Create an automatically balancing replica with a quorum size of 3 and backup count of 1
AtomixReplica replica = AtomixReplica.builder(new Address("123.456.789.3", 8700))
  .withTransport(new NettyTransport())
  .withStorage(new Storage(StorageLevel.MEMORY))
  .withClusterManager(BalancingClusterManager.builder()
    .withQuorumHint(3)
    .withBackupCount(1)
    .build())
  .build();

// Bootstrap a single node cluster
replica.bootstrap();
```

The `BalancingClusterManager` will automatically maintain the number of `ACTIVE` (Raft voting) replicas specified by
the `quorumHint`. As nodes join and leave the cluster, Atomix will promote or demote existing replicas as necessary to
ensure the cluster has *at least* `quorumHint` replicas at all times. Furthermore, the `BalancingClusterManager` will
maintain the provided `backupCount` `PASSIVE` replicas for each `ACTIVE` replica at any given time. Maintaining backup
replicas allows the cluster manager to quickly replace failed Raft nodes.

See the [Atomix website](http://atomix.io) for more information on configuring Atomix clusters or writing custom
resources.
