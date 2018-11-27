# Atomix Vert.x Cluster Manager

This project provides a cluster manager for [Vert.x](http://vertx.io) built on the [Atomix](http://atomix.io) distributed systems framework.

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

To use the Atomix cluster manager, simply add the Atomix jar to your classpath. By default, when the Atomix cluster manager is loaded it will load the Atomix configuration from an `atomix.conf` file on the classpath. An configuration reference can be found [on the website](https://atomix.io/docs/latest/user-manual/configuration/reference/). You can also provide a custom configuration file by passing the file name to the `AtomixClusterManager` constructor.

To use the `AtomixClusterManager` programmatically, construct an `Atomix` instance and pass it to the constructor.

```java
// Build the Atomix instance from code
Atomix atomix = Atomix.builder()
  .withMemberId("member-1")
  .withHost("192.168.10.2")
  .withPort(5679)
  .withMulticastEnabled()
  .withMembershipProvider(MulticastDiscoveryProvider.builder()
    .withBroadcastInterval(Duration.ofSeconds(1))
    .build())
  .withMembershipProtocol(SwimMembershipProtocol.builder()
    .withBroadcastUpdates(true)
    .withProbeInterval(Duration.ofSeconds(1))
    .withFailureTimeout(Duration.ofSeconds(5))
    .build())
  .withManagementGroup(RaftPartitionGroup.builder()
    .withNumPartitions(1)
    .withMembers("member-1", "member-2", "member-3")
    .build())
  .withPartitionGroups(PrimaryBackupPartitionGroup.builder()
    .withNumPartitions(32)
    .build())
  .build();

// Construct a Vert.x AtomixClusterManager
ClusterManager clusterManager = new AtomixClusterManager(atomix);

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

See the [Atomix website](http://atomix.io) for more information on configuring Atomix clusters.
