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
 sbt procesor / "run 2551 8551"
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
Forwarding message for entity -131145138 to cluster sharding
user event message for user id -131145138
Forwarding message for entity -1608152385 to cluster sharding
user event message -1608152385
```

THe first log line is just after the message has been taken from Kafka.
The second log is from the sharded entity. The goal is to have these
always on the same node as the dynamic partitioner will move the hsard to where ever the
Kafka partition is being consumed.

As there is only one node we get 100% locallity, each forwarded message is processed on the same node

Now let's see that remain true once we add more nodes to the Akka Cluster, add another with different ports:

```
 sbt procesor / "run 2552 8552"
```

When this starts up we'll see Kafka assign partitions to the new node (it is in the same consumer group):

```
Partition [29] assigned to current node. Updating shard allocation
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

