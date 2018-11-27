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

To use the Atomix cluster manager, simply add the Atomix jar to your classpath. By default, when the Atomix cluster manager is loaded it will load the Atomix configuration from an `atomix.conf` file on the classpath.

`atomix.conf`
```
cluster {
  memberId: member-1
  host: 192.168.10.2
  port: 5679

  multicast.enabled: true

  discovery {
    type: multicast
    broadcastInterval: 1s
  }

  protocol {
    type: swim
    broadcastUpdates: true
    probeInterval: 1s
    failureTimeout: 5s
  }
}

managementGroup {
  type: raft
  partitions: 1
  members: [member-1, member-2, member-3]
}

partitionGroups.data {
  type: primary-backup
  partitions: 32
}
```

A complete configuration reference can be found [on the website](https://atomix.io/docs/latest/user-manual/configuration/reference/).

```java
ClusterManager clusterManager = new AtomixClusterManager();
```

You can also provide a custom configuration file by passing the file name to the `AtomixClusterManager` constructor.

```java
ClusterManager clusterManager = new AtomixClusterManager("my.conf");
```

To use the `AtomixClusterManager` programmatically, construct an `Atomix` instance and pass it to the constructor.

```java
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
  .withPartitionGroups(PrimaryBackupPartitionGroup.builder("data")
    .withNumPartitions(32)
    .build())
  .build();

ClusterManager clusterManager = new AtomixClusterManager(atomix);
```

Once the cluster manager has been constructed, configure Vert.x to use the Atomix cluster manager and start a clustered instance.

```java
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
