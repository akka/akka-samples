# Akka CQRS - Scala Sample

This tutorial contains a sample illustrating an CQRS design with [Akka Cluster Sharding](https://doc.akka.io/docs/akka/2.6/typed/cluster-sharding.html), [Akka Cluster Singleton](https://doc.akka.io/docs/akka/2.6/typed/cluster-singleton.html), [Akka Persistence](https://doc.akka.io/docs/akka/2.6/typed/persistence.html), [Akka Persistence Query](https://doc.akka.io/docs/akka/2.6/persistence-query.html), and [Akka Projection](https://doc.akka.io/docs/akka-projection/1.0.0/index.html).

## Overview

This sample application implements a CQRS-ES design that will side-effect in the read model on selected events persisted to Cassandra by the write model. In this sample, the side-effect is logging a line. A more practical example would be to send a message to a Kafka topic or update a relational database.

## Write model

The write model is a shopping cart.

The implementation is based on a sharded actor: each `ShoppingCart` is an [Akka Cluster Sharding](https://doc.akka.io/docs/akka/2.6/typed/cluster-sharding.html) entity. The entity actor `ShoppingCart` is an [EventSourcedBehavior](https://doc.akka.io/docs/akka/2.6/typed/persistence.html).

Events from the shopping carts are tagged and consumed by the read model.

## Read model

The read model is implemented in such a way that 'load' is sharded over a number of processors. This number is `event-processor.parallelism`.
This is implemented using [Akka Projections](https://doc.akka.io/docs/akka-projection/current) which is then running on top of
 [Sharded Daemon Process](https://doc.akka.io/docs/akka/current/typed/cluster-sharded-daemon-process.html).

## Running the sample code

1. Start a Cassandra server by running:

```bash
sbt "runMain sample.cqrs.Main cassandra"
```

2. Start a node that runs the write model:

```bash
sbt -Dakka.cluster.roles.0=write-model "runMain sample.cqrs.Main 2551"
```

3. Start a node that runs the read model:

```bash
sbt -Dakka.cluster.roles.0=read-model "runMain sample.cqrs.Main 2552"
```

4. More write or read nodes can be started started by defining roles and port:

```bash
sbt -Dakka.cluster.roles.0=write-model "runMain sample.cqrs.Main 2553"
sbt -Dakka.cluster.roles.0=read-model "runMain sample.cqrs.Main 2554"
```

Try it with curl:

```bash
# add item to cart
curl -X POST -H "Content-Type: application/json" -d '{"cartId":"cart1", "itemId":"socks", "quantity":3}' http://127.0.0.1:8051/shopping/carts

# get cart
curl http://127.0.0.1:8051/shopping/carts/cart1

# update quantity of item
curl -X PUT -H "Content-Type: application/json" -d '{"cartId":"cart1", "itemId":"socks", "quantity":5}' http://127.0.0.1:8051/shopping/carts

# check out cart
curl -X POST -H "Content-Type: application/json" -d '{}' http://127.0.0.1:8051/shopping/carts/cart1/checkout
```

or same `curl` commands to port `8052`.
