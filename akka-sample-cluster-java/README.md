This tutorial contains 4 samples illustrating different [Akka cluster](http://doc.akka.io/docs/akka/2.5/java/cluster-usage.html) features.

- Subscribe to cluster membership events
- Sending messages to actors running on nodes in the cluster
- Cluster aware routers
- Cluster metrics

## A Simple Cluster Example

Open [application.conf](src/main/resources/application.conf).

To enable cluster capabilities in your Akka project you should, at a minimum, add the remote settings, and use `cluster` for `akka.actor.provider`. The `akka.cluster.seed-nodes` should normally also be added to your application.conf file.

The seed nodes are configured contact points which newly started nodes will try to connect with in order to join the cluster.

Note that if you are going to start the nodes on different machines you need to specify the ip-addresses or host names of the machines in `application.conf` instead of `127.0.0.1`.

Open [SimpleClusterApp.java](src/main/java/sample/cluster/simple/SimpleClusterApp.java).

The small program together with its configuration starts an ActorSystem with the Cluster enabled. It joins the cluster and starts an actor that logs some membership events. Take a look at the [SimpleClusterListener](src/main/java/sample/cluster/simple/SimpleClusterListener.java) actor.

You can read more about the cluster concepts in the [documentation](http://doc.akka.io/docs/akka/2.5/java/cluster-usage.html).

To run this sample, type `sbt "runMain sample.cluster.simple.SimpleClusterApp"` if it is not already started. If you are using maven, use `mvn compile exec:java -Dexec.mainClass="sample.cluster.simple.SimpleClusterApp"` instead. To pass parameters to maven add `-Dexec.args="..."`.

`SimpleClusterApp` starts three actor systems (cluster members) in the same JVM process. It can be more interesting to run them in separate processes. Stop the application and then open three terminal windows.

In the first terminal window, start the first seed node with the following command:

    sbt "runMain sample.cluster.simple.SimpleClusterApp 2551"

2551 corresponds to the port of the first seed-nodes element in the configuration. In the log output you see that the cluster node has been started and changed status to 'Up'.

In the second terminal window, start the second seed node with the following command:

    sbt "runMain sample.cluster.simple.SimpleClusterApp 2552"

2552 corresponds to the port of the second seed-nodes element in the configuration. In the log output you see that the cluster node has been started and joins the other seed node and becomes a member of the cluster. Its status changed to 'Up'.

Switch over to the first terminal window and see in the log output that the member joined.

Start another node in the third terminal window with the following command:

    sbt "runMain sample.cluster.simple.SimpleClusterApp 0"

Now you don't need to specify the port number, 0 means that it will use a random available port. It joins one of the configured seed nodes. Look at the log output in the different terminal windows.

Start even more nodes in the same way, if you like.

Shut down one of the nodes by pressing 'ctrl-c' in one of the terminal windows. The other nodes will detect the failure after a while, which you can see in the log output in the other terminals.

Look at the source code of the actor again. It registers itself as subscriber of certain cluster events. It gets notified with an snapshot event, `CurrentClusterState` that holds full state information of the cluster. After that it receives events for changes that happen in the cluster.

## Worker Dial-in Example

In the previous sample we saw how to subscribe to cluster membership events. You can read more about it in the [documentation](http://doc.akka.io/docs/akka/2.5/java/cluster-usage.html#Subscribe_to_Cluster_Events). How can cluster membership events be used?

Let's take a look at an example that illustrates how workers, here named *backend*, can detect and register to new master nodes, here named *frontend*.

The example application provides a service to transform text. When some text is sent to one of the frontend services, it will be delegated to one of the backend workers, which performs the transformation job, and sends the result back to the original client. New backend nodes, as well as new frontend nodes, can be added or removed to the cluster dynamically.

Open [TransformationMessages.java](src/main/java/sample/cluster/transformation/TransformationMessages.java). It defines the messages that are sent between the actors.

The backend worker that performs the transformation job is defined in [TransformationBackend.java](src/main/java/sample/cluster/transformation/TransformationBackend.java).

Note that the `TransformationBackend` actor subscribes to cluster events to detect new, potential, frontend nodes, and send them a registration message so that they know that they can use the backend worker.

The frontend that receives user jobs and delegates to one of the registered backend workers is defined in [TransformationFrontend.java](src/main/java/sample/cluster/transformation/TransformationFrontend.java).

Note that the `TransformationFrontend` actor watch the registered backend to be able to remove it from its list of available backend workers. Death watch uses the cluster failure detector for nodes in the cluster, i.e. it detects network failures and JVM crashes, in addition to graceful termination of watched actor.

To run this sample, type `sbt "runMain sample.cluster.transformation.TransformationApp"` if it is not already started.

TransformationApp starts 5 actor systems (cluster members) in the same JVM process. It can be more interesting to run them in separate processes. Stop the application and run the following commands in separate terminal windows.

    sbt "runMain sample.cluster.transformation.TransformationFrontendMain 2551"

    sbt "runMain sample.cluster.transformation.TransformationBackendMain 2552"

    sbt "runMain sample.cluster.transformation.TransformationBackendMain 0"

    sbt "runMain sample.cluster.transformation.TransformationBackendMain 0"

    sbt "runMain sample.cluster.transformation.TransformationFrontendMain 0"

## Cluster Aware Routers

All [routers](http://doc.akka.io/docs/akka/2.5/java/routing.html) can be made aware of member nodes in the cluster, i.e. deploying new routees or looking up routees on nodes in the cluster. When a node becomes unreachable or leaves the cluster the routees of that node are automatically unregistered from the router. When new nodes join the cluster additional routees are added to the router, according to the configuration. Routees are also added when a node becomes reachable again, after having been unreachable.

You can read more about cluster aware routers in the [documentation](http://doc.akka.io/docs/akka/2.5/java/cluster-usage.html#Cluster_Aware_Routers).

Let's take a look at a few samples that make use of cluster aware routers.

## Router Example with Group of Routees

Let's take a look at how to use a cluster aware router with a group of routees, i.e. a router which does not create its routees but instead forwards incoming messages to a given set of actors created elsewhere.

The example application provides a service to calculate statistics for a text. When some text is sent to the service it splits it into words, and delegates the task to count number of characters in each word to a separate worker, a routee of a router. The character count for each word is sent back to an aggregator that calculates the average number of characters per word when all results have been collected.

Open [StatsMessages.java](src/main/java/sample/cluster/stats/StatsMessages.java). It defines the messages that are sent between the actors.

The worker that counts number of characters in each word is defined in [StatsWorker.java](src/main/java/sample/cluster/stats/StatsWorker.java).

The service that receives text from users and splits it up into words, delegates to workers and aggregates is defined in [StatsService.java](src/main/java/sample/cluster/stats/StatsService.java) and [StatsAggregator.java](src/main/java/sample/cluster/stats/StatsService.java).

Note, nothing cluster specific so far, just plain actors.

All nodes start `StatsService` and `StatsWorker` actors. Remember, routees are the workers in this case.

Open [stats1.conf](src/main/resources/stats1.conf). The router is configured with `routees.paths`. This means that user requests can be sent to `StatsService` on any node and it will use`StatsWorker` on all nodes.

To run this sample, type `sbt "runMain sample.cluster.stats.StatsSampleMain"` if it is not already started.

StatsSampleMain starts 4 actor systems (cluster members) in the same JVM process. It can be more interesting to run them in separate processes. Stop the application and run the following commands in separate terminal windows.

    sbt "runMain sample.cluster.stats.StatsSampleMain 2551"

    sbt "runMain sample.cluster.stats.StatsSampleMain 2552"

    sbt "runMain sample.cluster.stats.StatsSampleClientMain"

    sbt "runMain sample.cluster.stats.StatsSampleMain 0"

## Router Example with Pool of Remote Deployed Routees

Let's take a look at how to use a cluster aware router on single master node that creates and deploys workers instead of looking them up.

Open [StatsSampleOneMasterMain.java](src/main/java/sample/cluster/stats/StatsSampleOneMasterMain.java). To keep track of a single master we use the [Cluster Singleton](http://doc.akka.io/docs/akka/2.5/contrib/cluster-singleton.html) in the contrib module. The `ClusterSingletonManager` is started on each node.

We also need an actor on each node that keeps track of where current single master exists and delegates jobs to the `StatsService`. That is provided by the `ClusterSingletonProxy`.

The `ClusterSingletonProxy` receives text from users and delegates to the current `StatsService`, the single master. It listens to cluster events to lookup the `StatsService` on the oldest node.

All nodes start `ClusterSingletonProxy` and the `ClusterSingletonManager`. The router is now configured in [stats2.conf](src/main/resources/stats2.conf).

To run this sample, type `sbt "runMain sample.cluster.stats.StatsSampleOneMasterMain"` if it is not already started.

StatsSampleOneMasterMain starts 4 actor systems (cluster members) in the same JVM process. It can be more interesting to run them in separate processes. Stop the application and run the following commands in separate terminal windows.

    sbt "runMain sample.cluster.stats.StatsSampleOneMasterMain 2551"

    sbt "runMain sample.cluster.stats.StatsSampleOneMasterMain 2552"

    sbt "runMain sample.cluster.stats.StatsSampleOneMasterClientMain"

    sbt "runMain sample.cluster.stats.StatsSampleOneMasterMain 0"

## Adaptive Load Balancing

The member nodes of the cluster collects system health metrics and publishes that to other nodes and to registered subscribers. This information is primarily used for load-balancing routers, such as the `AdaptiveLoadBalancingPool` and `AdaptiveLoadBalancingGroup` routers.

You can read more about cluster metrics in the [documentation](http://doc.akka.io/docs/akka/2.5/java/cluster-usage.html#Cluster_Metrics).

Let's take a look at this router in action. What can be more demanding than calculating factorials?

The backend worker that performs the factorial calculation is defined in [FactorialBackend.java](src/main/java/sample/cluster/factorial/FactorialBackend.java).

The frontend that receives user jobs and delegates to the backends via the router is defined in [FactorialFrontend.java](src/main/java/sample/cluster/factorial/FactorialFrontend.java).

As you can see, the router is defined in the same way as other routers, and in this case it is configured in [factorial.conf](src/main/resources/factorial.conf).

It is only router type `adaptive` and the `metrics-selector` that is specific to this router, other things work in the same way as other routers.

To run this sample, type `sbt "runMain sample.cluster.factorial.FactorialApp"` if it is not already started.

FactorialApp starts 4 actor systems (cluster members) in the same JVM process. It can be more interesting to run them in separate processes. Stop the application and run the following commands in separate terminal windows.

    sbt "runMain sample.cluster.factorial.FactorialBackendMain 2551"

    sbt "runMain sample.cluster.factorial.FactorialBackendMain 2552"

    sbt "runMain sample.cluster.factorial.FactorialBackendMain 0"

    sbt "runMain sample.cluster.factorial.FactorialFrontendMain 0"

Press ctrl-c in the terminal window of the frontend to stop the factorial calculations.

## Tests

Tests can be found in [src/multi-jvm](src/multi-jvm). You can run them by typing `sbt multi-jvm:test`.

