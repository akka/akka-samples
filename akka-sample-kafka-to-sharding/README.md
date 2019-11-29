# Aligning Kafka Partitions with Akka Cluster Sharding 

It is common to consume a Kafka topic and forward the messages to sharded actors. 

The Kafka consumer can be started on each node with the same group id
and then the messages forwarded to sharding via an ask. It is important to use  ask
rather than tell to enable backpressure from the sharded actor to the Kafka consumer. 

Using the default shard allocation strategy there is no relation between the Kafka partitions
allocated to a consumer and the location of the shards meaning that most messages will 
have one network hop.

If all of the messages for the same sharded entity are in the same Kafka partition then
this can be improved on with the dynamic shard allocation strategy.
For this to be true the producer partitioning must align with the shard extraction 
in cluster sharding. 

Imagine a scenario that processes all events for users with following constraints:
 * The key of the kafka message is the user id which is in turn the entity id in sharding
 * All messages for the same user id end up in the same partition
 
Then we can enforce that the kafka partition == the akka cluster shard id and use the dynamic
sharding allocation strategy to move shards to the node that is consuming that partition, resulting
in no cross node traffic.

# Running the sample 

The sample is made up of two applications:
* `producer` A Kafka producer, that produces events about users 
* `processor` An Akka Cluster Sharding application that reads the Kafka topic and forwards the messages to a sharded
              entity that represents a user
              
The sample demonstrates how the dynamic shard allocation strategy can used so messages are processed locally.

* Create a topic with many 128 partitions, or update application.conf with the desired number of
  partitions e.g. a command from your Kafka installation:
  
```
  bin/kafka-topics.sh --create --bootstrap-server localhost:9092 --replication-factor 1 --partitions 128 --topic user-events
```
  
* Start a single node, two arguments are required, the akka remoting port and an akka management port. 
  Seed nodes of 2551 and 2552 are setup so these two akka nodes must be started first 
* As there is a single consumer, all partitions will initially be assigned to this node.

```
 sbt procesor / "run 2551 8551 8081"
```

If there are existing messages on the topic they will all be processed locally as there is a single node.

* Start the Kafka producer to see some messages flowing from Kafka to sharding.

```
sbt producer / run
```

In the producer window you'll see:

```
Sending message to user 1527972333
Sending message to user -233160412
Sending message to user 1756778986
Sending message to user -1059178536
```

In the single processor node the messages will start flowing:

```
Forwarding message for entity 25 to cluster sharding
user 25 purchase cat t-shirt, quantity 3, price 2080
Forwarding message for entity 13 to cluster sharding
user 13 purchase cat t-shirt, quantity 2, price 898 
```

THe first log line is just after the message has been taken from Kafka.
The second log is from the sharded entity. The goal is to have these
always on the same node as the dynamic partitioner will move the hsard to where ever the
Kafka partition is being consumed.

As there is only one node we get 100% locallity, each forwarded message is processed on the same node

Now let's see that remain true once we add more nodes to the Akka Cluster, add another with different ports:

```
 sbt procesor / "run 2552 8552 8082"
```

When this starts up we'll see Kafka assign partitions to the new node (it is in the same consumer group):

```
Partition [29] assigned to current node. Updating shard allocation
```

Followed by the rebalance that moves the shards:

```
 Starting rebalance for shards [45,34,12,51,8,19,23,62,4,40,15,11,9,44,33,22,56,55,26,50,37,61,13,46,24,35,16,5,10,59,48,21,54,43,57,32,49,6,36,1,39,17,25,60,14,47,31,58,53,42,0,20,27,2,38,18,30,7,29,41,63,3,52,28]. Current shards rebalancing: []
```

Both nodes now have roughly 64 shards / partitions, all co-located.

After some period of settling down then each node in the cluster should be processing about number of messages
with each `Forward message for entity X` being followed by a `user event message for user id x` on the same node.

Using Akka management we can see the shard allocations and the number of entities per shard (uses `curl` and `jq`):

```

// Node 1:
 curl -v localhost:8551/cluster/shards/user-processing  | jq

// Node 2:
 curl -v localhost:8552/cluster/shards/user-processing  | jq
```

We can count the number of shards on each:

```
curl -v localhost:8551/cluster/shards/user-processing  | jq -r "." | grep shardId  | wc
```

This should return 64 on each node.


We now have a 2 node Akka Cluster with a Kafka Consumer running on each where the kafka partitions allign
with Cluster shards.

A use case for sending the processing to sharding is it allows each entity to be queried from any where in the cluster
e.g. from a HTTP or gRPC front end.

The sample includes a gRPC front end that gets the running total of number of purchases and total money spent
by each customer. Requests can come via gRPC on any node for any entity but sharding will route them to
the correct node even if that moves due to a kafka rebalance.

A gRPC client is included which can be started with...

```
 sbt client/ run
```

It assumes there is one of the nodes running its front end port on 8081. The users are `0-99`

```
7
User 7 has made 2 for a total of 3096p
Enter user id or :q to quit
3
User 3 has made 1 for a total of 12060p
Enter user id or :q to quit
4
User 4 has made 1 for a total of 7876p
Enter user id or :q to quit
5
User 5 has made 0 for a total of 0p
Enter user id or :q to quit
1
User 1 has made 0 for a total of 0p
Enter user id or :q to quit
```

We've now demonstrated two things:

* Keeping the processing local, where ever the Kafka partition is consumed the shard will be moved to that location
* The state for each entity is globally accessible from all nodes 

