# Atomix Vert.x Cluster Manager

This project provides a cluster manager for [Vert.x](http://vertx.io) built on the [Atomix](http://atomix.io) distributed coordination framework.

### Installation

Add the Maven dependency to your `pom.xml`:

```
<dependency>
  <groupId>io.atomix.vertx</groupId>
  <artifactId>atomix-vertx</artifactId>
  <version>0.1.0-SNAPSHOT
</dependency>
```

### Usage

To use the `AtomixClusterManager`, construct an `Atomix` instance via either `AtomixClient` or `AtomixReplica`.

The `AtomixReplica` is a stateful node that stores the state of data structures. When constructing a replica,
the replica must be assigned a local `Address` to which to bind its server and a list of one or more
remote replica `Address`es:

```java
Address address = new Address("123.456.789.0", 5000);

Collection<Address> members = Arrays.asList(
  new Address("123.456.789.0", 5000),
  new Address("123.456.789.1", 5000),
  new Address("123.456.789.2", 5000)
);

Atomix atomix = AtomixReplica.builder(address, members)
  .withTransport(new NettyTransport())
  .withStorage(new Storage(StorageLevel.MEMORY))
  .build();

ClusterManager manager = new AtomixClusterManager(atomix);

VertxOptions options = new VertxOptions().setClusterManager(manager);

Vertx.clusteredVertx(options, res -> {
  if (res.succeeded()) {
    Vertx vertx = res.result();
  } else {
    // failed!
  }
});
```

The `AtomixClient` is a stateless node that accesses data structures on remove `AtomixReplica` nodes.
When constructing a client, the client must be given a list of one or more accessible replica `Address`es:

```java
Collection<Address> members = Arrays.asList(
  new Address("123.456.789.0", 5000),
  new Address("123.456.789.1", 5000),
  new Address("123.456.789.2", 5000)
);

Atomix atomix = AtomixClient.builder(address, members)
  .withTransport(new NettyTransport())
  .build();

ClusterManager manager = new AtomixClusterManager(atomix);

VertxOptions options = new VertxOptions().setClusterManager(manager);

Vertx.clusteredVertx(options, res -> {
  if (res.succeeded()) {
    Vertx vertx = res.result();
  } else {
    // failed!
  }
});
```
