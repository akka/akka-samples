This tutorial contains a sample illustrating an CQRS design with [Akka Cluster Sharding](http://doc.akka.io/docs/akka/current/scala/cluster-sharding.html), [Akka Cluster Singleton](http://doc.akka.io/docs/akka/current/cluster-singleton.html), [Akka Persistence](http://doc.akka.io/docs/akka/current/scala/persistence.html) and [Akka Persistence](http://doc.akka.io/docs/akka/current/scala/persistence-query.html).

## Overview

This sample application implements a CQRS-ES design that will side-effect in the read model on selected events persisted to Cassandra by the write model. In this sample, the side-effect is logging a line. A more practical example would be to send a message to a Kafka topic.

## A sample write model

A very simple write model is defined that models a simple [network] *switch*.

The following commands are defined on this model:

- Creation of a new *switch* with a given number of ports and in disabled state. The *entity ID* is the name of the switch. 
- Changing the state of a *port* on a *switch*
- Posting the state of the *switch:* the resulting event will be tagged for processing by the sharded read model. The actual tag is computed from the *entity ID*: it has a prefix (configurable by setting `event-processor.tag-prefix`) followed by a number from zero to `event-processor.parallelism`)

The implementation is based on a sharded actor: each *switch* is an [Akka Cluster Sharding](http://doc.akka.io/docs/akka/current/scala/cluster-sharding.html) entity. The entity actor [`Switch`] actually is a [Persistent Actor](http://doc.akka.io/docs/akka/current/scala/persistence.html).

## A sample read model

The read model is implemented in such a way that 'load' is sharded over a number of processors. This number is `event-processor.parallelism`.

The implementation is resilient: it uses an *Akka Cluster Singleton* in combination with *Akka Cluster Sharding*.

## Running the sample code

1. Start a Cassandra server by running `sbt "run cassandra"`
2. Start a node that runs the write-model: `sbt wmodel`
3. Start one or more nodes that will run the read model: `sbt rmodel1`, `sbt rmodel2`, `sbt rmodel3`

> Note: When starting-up the application when all nodes are down, cluster formation will not take place before the node running the write model is started as this node is the first Akka Cluster seed-node  