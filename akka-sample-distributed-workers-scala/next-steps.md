# Next Steps

The following are some ideas where to take this sample next. Implementation of each idea is left up to you.

## A HTTP Based API

The `FrontEnd` in this sample is a dummy that automatically generates work. A real application could for example use [Akka HTTP](http://doc.akka.io/docs/akka-http/current/scala/http/introduction.html) to provide a HTTP REST (or other) API for external clients.

## Scaling better with many masters

If the singleton master becomes a bottleneck we could start several master actors and shard the jobs among them. This could be achieved by using [Akka Cluster Sharding](http://doc.akka.io/docs/akka/current/scala/cluster-sharding.html) with many `Master` actors as entities and a hash of some sort on the payload deciding which master it should go to.

## More tools for building distributed systems

In this example we have used
[Cluster Singleton](http://doc.akka.io/docs/akka/current/scala/cluster-singleton.html#cluster-singleton)
and
[Distributed Publish Subscribe](http://doc.akka.io/docs/akka/current/scala/distributed-pub-sub.html)
 but those are not the only tools in Akka Cluster. 
 
 You can also find a good overview of the various modules that make up Akka in 
 [this section of the official documentation](http://doc.akka.io/docs/akka/current/scala/guide/modules.html#cluster-singleton)