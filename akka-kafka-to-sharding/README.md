# Aligning Kafka Partitions with Akka Cluster Sharding 

It is common to consume a Kafka topic and forward the messages to sharded actors. 

The Kafka consumer can be started on each node with the same group id
and then the messages forwarded to sharding via an ask. It is important to use  ask
rather than tell to enable backpressure from the sharded actor to the Kafka consumer. 

Using the default shard allocation strategy there is no relation between the Kafka partitions
allocated to a consumer and the location of the shards.

If all of the messages for the same sharded entity are in the same Kafka partition then
this can be improved on with the dynamic shard allocation strategy.
For this to be true the producer partitioning must align with the shard extraction 
in cluster sharding. 

For example. A system that processes all events for users. The key of the kafka message
is the user id. All messages for the same user end up in the same partition.

The user id is also the entity id for sharding and the kafka partition becomes
the shard in cluster sharding.

There doesn't need to be a one to one mapping between shards but for this example
it keeps things simpler.



