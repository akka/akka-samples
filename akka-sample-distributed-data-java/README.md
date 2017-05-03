This tutorial contains 5 samples illustrating how to use [Akka Distributed Data](http://doc.akka.io/docs/akka/2.5/java/distributed-data.html).

- Low Latency Voting Service
- Highly Available Shopping Cart
- Distributed Service Registry
- Replicated Cache
- Replicated Metrics

**Akka Distributed Data** is useful when you need to share data between nodes in an Akka Cluster. The data is accessed with an actor providing a key-value store like API. The keys are unique identifiers with type information of the data values. The values are _Conflict Free Replicated Data Types_ (CRDTs).

All data entries are spread to all nodes, or nodes with a certain role, in the cluster via direct replication and gossip based dissemination. You have fine grained control of the consistency level for reads and writes.

The nature CRDTs makes it possible to perform updates from any node without coordination. Concurrent updates from different nodes will automatically be resolved by the monotonic merge function, which all data types must provide. The state changes always converge. Several useful data types for counters, sets, maps and registers are provided and you can also implement your own custom data types.

It is eventually consistent and geared toward providing high read and write availability (partition tolerance), with low latency. Note that in an eventually consistent system a read may return an out-of-date value.

Note that there are some [Limitations](http://doc.akka.io/docs/akka/2.5/java/distributed-data.html#Limitations) that you should be aware of. For example, Akka Distributed Data is not intended for _Big Data_.

## Low Latency Voting Service

Distributed Data is great for low latency services, since you can update or get data from the local replica without immediate communication with other nodes.

Open [VotingService.java](src/main/java/sample/distributeddata/VotingService.java).

`VotingService` is an actor for low latency counting of votes on several cluster nodes and aggregation of the grand total number of votes. The actor is started on each cluster node. First it expects an `OPEN` message on one or several nodes. After that the counting can begin. The open signal is immediately replicated to all nodes with a boolean [Flag](http://doc.akka.io/docs/akka/2.5/java/distributed-data.html#Flags_and_Registers). Note `writeAll`.

    Update<Flag> update = new Update<>(openedKey, Flag.create(), writeAll, curr -> curr.switchOn());

The actor is subscribing to changes of the `OpenedKey` and other instances of this actor, also on other nodes, will be notified when the flag is changed.

    replicator.tell(new Subscribe<>(openedKey, self()), ActorRef.noSender());

    .match(Changed.class, c -> c.key().equals(openedKey), c -> receiveOpenedChanged((Changed<Flag>) c))

The counters are kept in a [PNCounterMap](http://doc.akka.io/docs/akka/2.5/java/distributed-data.html#Counters) and updated with:

    Update<PNCounterMap> update = new Update<>(countersKey, PNCounterMap.create(), Replicator.writeLocal(),
            curr -> curr.increment(node, vote.participant, 1));
     replicator.tell(update, self());

Incrementing the counter is very fast, since it only involves communication with the local `Replicator` actor. Note `writeLocal`. Those updates are also spread to other nodes, but that is performed in the background.

The total number of votes is retrieved with:

    Optional<Object> ctx = Optional.of(sender());
    replicator.tell(new Replicator.Get<PNCounterMap>(countersKey, readAll, ctx), self());

    .match(GetSuccess.class, g -> g.key().equals(countersKey),
       g -> receiveGetSuccess(open, (GetSuccess<PNCounterMap>) g))

    private void receiveGetSuccess(boolean open, GetSuccess<PNCounterMap> g) {
      Map<String, BigInteger> result = g.dataValue().getEntries();
      ActorRef replyTo = (ActorRef) g.getRequest().get();
      replyTo.tell(new Votes(result, open), self());
    }

The multi-node test for the `VotingService` can be found in [VotingServiceSpec.scala](src/multi-jvm/scala/sample/distributeddata/VotingServiceSpec.scala).

Read the [Using the Replicator](http://doc.akka.io/docs/akka/2.5/java/distributed-data.html#Using_the_Replicator) documentation for more details of how to use `Get`, `Update`, and `Subscribe`.

## Highly Available Shopping Cart

Distributed Data is great for highly available services, since it is possible to perform updates to the local node (or currently available nodes) during a network partition.

Open [ShoppingCart.java](src/main/java/sample/distributeddata/ShoppingCart.java).

`ShoppingCart` is an actor that holds the selected items to buy for a user. The actor instance for a specific user may be started where ever needed in the cluster, i.e. several instances may be started on different nodes and used at the same time.

Each product in the cart is represented by a `LineItem` and all items in the cart is collected in a [LWWMap](http://doc.akka.io/docs/akka/2.5/java/distributed-data.html#Maps).

The actor handles the commands `GET_CART`, `AddItem` and `RemoveItem`. To get the latest updates in case the same shopping cart is used from several nodes it is using consistency level of `readMajority` and `writeMajority`, but that is only done to reduce the risk of seeing old data. If such reads and writes cannot be completed due to a network partition it falls back to reading/writing from the local replica (see `GetFailure`). Local reads and writes will always be successful and when the network partition heals the updated shopping carts will be be disseminated by the [gossip protocol](https://en.wikipedia.org/wiki/Gossip_protocol) and the `LWWMap` CRDTs are merged, i.e. it is a highly available shopping cart.

The multi-node test for the `ShoppingCart` can be found in [ShoppingCartSpec.scala](src/multi-jvm/scala/sample/distributeddata/ShoppingCartSpec.scala).

Read the [Consistency](http://doc.akka.io/docs/akka/2.5/java/distributed-data.html#Consistency) section in the documentation to understand the consistency considerations.

## Distributed Service Registry

Have you ever had the need to lookup actors by name in an Akka Cluster? This example illustrates how you could implement such a registry. It is probably not feature complete, but should be a good starting point.

Open [ServiceRegistry.java](src/main/java/sample/distributeddata/ServiceRegistry.java).

`ServiceRegistry` is an actor that is started on each node in the cluster. It supports two basic commands:

- `Register` to bind an `ActorRef` to a name, several actors can be bound to the same name
- `Lookup` get currently bound services of a given name

For each named service it is using an [ORSet](http://doc.akka.io/docs/akka/2.5/java/distributed-data.html#Sets). Here we are using top level `ORSet` entries. An alternative would have been to use a `ORMultiMap` holding all services. That would have a disadvantage if we have many services. When a data entry is changed the full state of that entry is replicated to other nodes, i.e. when you update a map the whole map is replicated.

The `ServiceRegistry` is subscribing to changes of a `GSet` where we add the names of all services. It is also subscribing to all such service keys to get notifications when actors are added or removed to a named service.

The multi-node test for the `ServiceRegistry` can be found in [ServiceRegistrySpec.scala](src/multi-jvm/scala/sample/distributeddata/ServiceRegistrySpec.scala).

## Replicated Cache

This example illustrates a simple key-value cache.

Open [ReplicatedCache.scala](src/main/java/sample/distributeddata/ReplicatedCache.java).

`ReplicatedCache` is an actor that is started on each node in the cluster. It supports three commands: `PutInCache`, `GetFromCache` and `Evict`.

It is splitting up the key space in 100 top level keys, each with a `LWWMap`. When a data entry is changed the full state of that entry is replicated to other nodes, i.e. when you update a map the whole map is replicated. Therefore, instead of using one ORMap with 1000 elements it is more efficient to split that up in 100 top level ORMap entries with 10 elements each. Top level entries are replicated individually, which has the trade-off that different entries may not be replicated at the same time and you may see inconsistencies between related entries. Separate top level entries cannot be updated atomically together.

The multi-node test for the `ReplicatedCache` can be found in [ReplicatedCacheSpec.scala](src/multi-jvm/scala/sample/distributeddata/ReplicatedCacheSpec.scala).

## Replicated Metrics

This example illustrates to spread metrics data to all nodes in an Akka cluster.

Open [ReplicatedMetrics.java](src/main/java/sample/distributeddata/ReplicatedMetrics.java).

`ReplicatedMetrics` is an actor that is started on each node in the cluster. Periodically it collects some metrics, in this case used and max heap size. Each metrics type is stored in a `LWWMap` where the key in the map is the address of the node. The values are disseminated to other nodes with the gossip protocol.

The multi-node test for the `ReplicatedCache` can be found in [ReplicatedMetricsSpec.scala](src/multi-jvm/scala/sample/distributeddata/ReplicatedMetricsSpec.scala).

Note that there are some [Limitations](http://doc.akka.io/docs/akka/2.5/scala/distributed-data.html#Limitations) that you should be aware of. For example, Akka Distributed Data is not intended for _Big Data_.
