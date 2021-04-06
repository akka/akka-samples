# Akka Distributed Workers with Scala Guide

To be reactive, distributed applications must deal gracefully with temporary and prolonged outages as well as have
the ability to scale up and down to make the best use of resources.
Akka Cluster provides these capabilities so that you don't have to implement them yourself.
The distributed workers example demonstrates the following Akka clustering capabilities:

* elastic addition and removal of the front-end actors that accept client requests
* elastic addition and removal of the back-end actors that perform the work distribution of actors across different nodes
* how jobs are re-tried in the face of failures

The design is based on Derek Wyatt's blog post [Balancing Workload Across Nodes with Akka 2](http://letitcrash.com/post/29044669086/balancing-workload-across-nodes-with-akka-2) from 2012, which is a bit old, but still a good description of the advantages of letting the workers pull work from the work manager instead of pushing work to the workers.

## Running the example

To run the example:

```bash
sbt run
```

After waiting a few seconds for the cluster to form the output should start look _something_ like this (scroll all the way to the right to see the Actor output):

```bash
[INFO] [07/21/2017 17:41:53.320] [ClusterSystem-akka.actor.default-dispatcher-16] [akka://ClusterSystem@127.0.0.1:51983/user/producer] Produced work: 3
[INFO] [07/21/2017 17:41:53.322] [ClusterSystem-akka.actor.default-dispatcher-3] [akka://ClusterSystem@127.0.0.1:2551/user/master/singleton] Accepted work: 3bce4d6d-eaae-4da6-b316-0c6f566f2399
[INFO] [07/21/2017 17:41:53.328] [ClusterSystem-akka.actor.default-dispatcher-3] [akka://ClusterSystem@127.0.0.1:2551/user/master/singleton] Giving worker 2b646020-6273-437c-aa0d-4aad6f12fb47 some work 3bce4d6d-eaae-4da6-b316-0c6f566f2399
[INFO] [07/21/2017 17:41:53.328] [ClusterSystem-akka.actor.default-dispatcher-2] [akka://ClusterSystem@127.0.0.1:51980/user/worker] Got work: 3
[INFO] [07/21/2017 17:41:53.328] [ClusterSystem-akka.actor.default-dispatcher-16] [akka://ClusterSystem@127.0.0.1:51980/user/worker] Work is complete. Result 3 * 3 = 9.
[INFO] [07/21/2017 17:41:53.329] [ClusterSystem-akka.actor.default-dispatcher-19] [akka://ClusterSystem@127.0.0.1:2551/user/master/singleton] Work 3bce4d6d-eaae-4da6-b316-0c6f566f2399 is done by worker 2b646020-6273-437c-aa0d-4aad6f12fb47
```

Now take a look at what happened under the covers.

## What happens when you run it

When `Main` is run without any parameters, it starts six `ActorSystem`s in the same JVM. These six `ActorSystem`s form a single cluster. The six nodes include two each that perform front-end, back-end, and worker tasks:

* The front-end nodes simulate an external interface, such as a REST API, that accepts workloads from clients.
* The worker nodes have worker actors that accept and process workloads.
* The back-end nodes contain a WorkManager actor that coordinates workloads, keeps track of the workers, and delegates
   work to available workers. One of the nodes is active and one is on standby. If the active WorkManager goes down, the standby takes over.

Let's look at the details of each part of the application, starting with the front-end.

## Front end

Typically in systems built with Akka, clients submit requests using a RESTful API or a gRPC API.
Either [Akka HTTP](http://doc.akka.io/docs/akka-http/current/scala/http/introduction.html) or [Play Framework](https://www.playframework.com)
are great choices for implementing an HTTP API for the front-end, [Akka
gRPC](https://doc.akka.io/docs/akka-grpc/current/index.html) can be used of a gRPC front end is preferred.

To limit the scope of this example, we have chosen to emulate client activity with two ordinary actors:

* The `FrontEnd` actor generates payloads at random intervals and sends them to the 'WorkManager' actor.
* The `WorkResultConsumerActor` that consumes results and logs them.

The `FrontEnd` actor only concerns itself with posting workloads, and does not care when the work has been completed.
When a workload has been processed successfully and passed to the `WorkManager` actor it publishes the result to all interested cluster nodes through Distributed Pub-Sub.

The `WorkResultConsumerActor` subscribes to the completion events and logs when a workload has completed.

Now, let's take a look at the code that accomplishes this front-end behavior.

### The Front-end Actor

Note in the source code that as the 'FrontEnd' actor starts up, it:

1. Schedules 'Tick' messages to itself.
1. Each 'Tick' message:
    1. Triggers creation of a new 'Work' message.
    1. Switches to a new 'busy' behavior.
    1. Sends the 'Work' message to the 'WorkManager' actor of a 'back-end' node.

The `FrontEnd` actor schedules `Tick` messages to itself when starting up. the `Tick` message then triggers creation of a new `Work`, sending the work to the `WorkManager` actor on a `back-end` node and switching to a new `busy` behavior.

The cluster contains one `WorkManager` actor. The `FrontEnd` actor does not need to know the exact location because it sends work to the `masterProxy` that is a cluster singleton proxy.

The 'WorkManager' actor can accept or deny a work request and we need to deal with unexpected errors:

* If the 'WorkManager' accepts the request, the actor schedules a new tick to itself and toggles back to the idle behavior.
* To deal with failures, the message uses the [ask pattern](http://doc.akka.io/docs/akka/current/scala/actors.html#ask-send-and-receive-future) to add a timeout to wait for a reply. If the timeout expires before the master responds, the returned 'Future' fails with an akka.pattern.AskTimeoutException.
* We transform timeouts or denials from the 'WorkManager' into a 'Failed' value that is automatically piped back to `self` and a `Retry` is scheduled.

When a workload has been acknowledged by the master, the actor goes back to the  `idle` behavior which schedules
a `Tick` to start the process again.  

If the work is not accepted or there is no response, for example if the message or response got lost, the `FrontEnd` actor backs off a bit and then sends the work again.

You can see the how the actors on a front-end node is started in the method `Main.start` when the node
contains the `front-end` role:

### The Work Result Consumer Actor

As mentioned in the introduction, results are published using Distributed Pub-Sub. The 'WorkResultConsumerActor' subscribes to completion events and logs when a workload has completed.

In an actual application you would probably want a way for clients to poll or stream the status changes of the submitted work.

## Back end

The back-end nodes host the `WorkManager` actor, which manages work, keeps track of available workers,
and notifies registered workers when new work is available. The single `WorkManager` actor is the heart of the solution,
with built-in resilience provided by the [Akka Cluster Singleton](http://doc.akka.io/docs/akka/current/scala/guide/modules.html#cluster-singleton).

### The WorkManager singleton

The [Cluster Singleton](http://doc.akka.io/docs/akka/current/scala/guide/modules.html#cluster-singleton) tool in Akka makes sure an
actor only runs concurrently on one node within the subset of nodes marked with the role `back-end` at any given time.
It will run on the oldest back-end node. If the node on which the 'WorkManager' is running is removed from the cluster, Akka starts a new
`WorkManager` on the next oldest node. Other nodes in the cluster interact with the `WorkManager` through the `ClusterSingletonProxy` without
knowing the explicit location. You can see this interaction in the `FrontEnd` and `Worker` actors.

In case of the master node crashing and being removed from the cluster another master actor is automatically started on the new oldest node.

You can see how the master singleton is started in the method `init`
in `WorkManagerSingleton`:

The singleton accepts the `Behavior` of the actual singleton actor, as well as configuration
which allows us to decide that the singleton actors should only run on the nodes with the role `back-end`.

Calls to `init` on nodes without the `back-end` role will result in a proxy to communicate with the singleton
being created.

The state of the master is recovered on the standby node in the case of the node being lost through event sourcing.

Let's now explore the implementation of the `WorkManager` actor in depth.

## Work manager in detail

The `WorkManager` actor is without question the most involved component in this example.
This is because it is designed to deal with failures. While the Akka cluster takes care of restarting the `WorkManager` in case of a failure, we also want to make sure that the new `WorkManager` can arrive at the same state as the failed `WorkManager`. We use event sourcing and Akka Persistence to achieve this.

If the `back-end` node hosting the `WorkManager` actor would crash the Akka Cluster Singleton makes sure it starts up on a different node, but we would also want it to reach the exact same state as the crashed node `WorkManager`. This is achieved through use of event sourcing and [Akka Persistence](http://doc.akka.io/docs/akka/current/scala/persistence.html).

### Tracking current work items

The current set of work item is modelled in the `WorkState` class. It keeps track of the current set of work that is pending, has been accepted by a worker, has completed etc. Every change to the `WorkState` is modelled as a domain event.

This allows us to capture and store each such event that happens, we can later replay all of them on an empty model and
arrive at the exact same state. This is how event sourcing and [Akka Persistence](http://doc.akka.io/docs/akka/current/scala/persistence.html) allows the actor to start on any node and reach the same state as a previous instance.

If the `WorkManager` fails and is restarted, the replacement `WorkManager` replays events from the log to retrieve the current state. This means that when the WorkState is modified, the `WorkManager` must persist the event before acting on it. When the event is successfully stored, we can modify the state. Otherwise, if a failure occurs before the event is persisted, the replacement `WorkManager` will not be able to attain the same state as the failed `WorkManager`.

Let's look at how a command to process a work item from the front-end comes in. The first thing you might notice is the comment saying _idempotent_, this means that the same work message may arrive multiple times, but regardless how many times the same work arrives, it should only be executed once. This is needed since the `FrontEnd` actor re-sends work in case of the `Work` or `Ack` messages getting lost (Akka does not provide any guarantee of delivery, [see details in the docs](http://doc.akka.io/docs/akka/current/scala/general/message-delivery-reliability.html#discussion-why-no-guaranteed-delivery-)).

To make the logic idempotent we simple check if the work id is already known, and if it is we simply `Ack` it without further logic. If the work is previously unknown, we start by transforming it into a `WorkAccepted` event, which we persist,  and only in the `EventHandler` that is called after the event has been persisted do we actually update the `workState`, and send an `Ack` back to the `FrontEnd` and trigger a search for available workers. In this case the event handler delegates the logic to the `WorkState` domain class.

### Implementation items required for Akka Persistence

In a "normal" Actor the only thing we have only to provide a `Behavior`. For a `PersistentBehavior`
there are three things that needs to be implemented:

 1. `persistenceId` is a global identifier for the actor, we must make sure that there is never more than one Actor instance with the same `persistenceId`  running globally, or else we would possibly mess up its journal.
 1. `commandHandler` receives incoming messages, called `Command`s and returns any Effects e.g. persisting an event
 1. `eventHandler` is invoked with the events once they have been persisted to the database

### Tracking workers

Unlike the `WorkManager` actor, the example system contains multiple workers that can be stopped and restarted frequently.
We do not need to save their state since the `WorkManager` is tracking work and will simply send work to another worker
if the original fails to respond. So, rather than persisting a list of available workers, the example uses the following
strategy:

* Running workers periodically register with the master using a `RegisterWorker` message.
  If a `back-end` node fails and the `WorkManager` is started on a new node, the registrations go automatically to the new node.
* Any type of failure -- whether from the network, worker actor, or node -- that prevents a `RegisterWorker`
  message from arriving within the `work-timeout` period causes the 'WorkManager' actor to remove the worker from its list.

When stopping a `Worker` Actor still tries to gracefully remove it self using the `DeRegisterWorker` message,
but in case of crash it will have no chance to communicate that with the master node.

Now let's move on to the last piece of the puzzle, the worker nodes.

## Worker nodes

`Worker` actors and the `WorkManager` actor interact as follows:

1. `Worker` actors register with the receptionist.
1. The `WorkManager` subscribes to workers via the receptionist.
1. When the `WorkManager` actor has work, it sends a `WorkIsReady` message to all workers it thinks are not busy.
1. The `WorkManager` picks the first reply and assigns the work to that worker.
   This achieves back pressure because the `WorkManager` does not push work on workers that are already busy and overwhelm
   their mailboxes.
1. When the worker receives work from the master, it delegates the actual processing to a child actor, `WorkExecutor`.
   This allows the worker to be responsive while its child executes the work.

You can see how a worker node and a number of worker actors is started in the method `Main.start`
if the node contains the role `worker`.

Now that we have covered all the details, we can experiment with different sets of nodes for the cluster.

## Experimenting

When running the appliction without parameters it runs a six node cluster within the same JVM and starts a Cassandra database. It can be more interesting to run them in separate processes. Open four terminal windows.

In the first terminal window, start the Cassandra database with the following command:

```bash
sbt "runMain worker.Main cassandra"
```

The Cassandra database will stay alive as long as you do not kill this process, when you want to stop it you can do that with `Ctrl + C`. Without the database the back-end nodes will not be able to start up.

You could also run your own local installation of Cassandra given that it runs on the default port on localhost and does not require a password.

With the database running, go to the second terminal window and start the first seed node with the following command:

```bash
sbt "runMain worker.Main 2551"
```

2551 corresponds to the port of the first seed-nodes element in the configuration. In the log output you see that the cluster node has been started and changed status to 'Up'.

In the third terminal window, start the front-end node with the following command:

```bash
sbt "runMain worker.Main 3001"
```

3001 is to the port of the node. In the log output you see that the cluster node has been started and joins the 2551 node and becomes a member of the cluster. Its status changed to 'Up'.

Switch over to the second terminal window and see in the log output that the member joined. So far, no `Worker` has not been started, i.e. jobs are produced and accepted but not processed.

In the fourth terminal window, start a worker node with the following command:

```bash
sbt "runMain worker.Main 5001 3"
```

5001 means the node will be a worker node, and the second parameter `3` means that it will host three worker actors.

Look at the log output in the different terminal windows. In the second window (front-end) you should see that the produced jobs are processed and logged as `"Consumed result"`.

Take a look at the logging that is done in `WorkProducer`, `WorkManager` and `Worker`. Identify the corresponding log entries in the 3 terminal windows with Akka nodes.

Shutdown the worker node (fourth terminal window) with `ctrl-c`. Observe how the `"Consumed result"` logs in the front-end node (second terminal window) stops. Start the worker node again.

```bash
sbt "runMain worker.Main 5001 3"
```

You can also start more such worker nodes in new terminal windows.

You can start more cluster back-end nodes using port numbers between 2000-2999.

```bash
sbt "runMain worker.Main 2552"
```

The nodes with port 2551 to 2554 are configured to be used as "seed nodes" in this sample, if you shutdown all or start none of these the other nodes will not know how to join the cluster. If all four are shut down and 2551 is started it will join itself and form a new cluster.

As long as one of the four nodes is alive the cluster will keep working. You can read more about this in the [Akka documentation section on seed nodes](http://doc.akka.io/docs/akka/current/scala/cluster-usage.html).

You can start more cluster front-end nodes using port numbers between 3000-3999:

```bash
sbt "runMain worker.Main 3002
```

Any port outside these ranges creates a worker node, for which you can also play around with the number of worker actors on using the second parameter.

```bash
sbt "runMain worker.Main 5009 4
```

## The journal

The files of the Cassandra database are saved in the target directory and when you restart the application the state is recovered. You can clean the state with:

```bash
sbt clean
```

## Next steps

The following are some ideas where to take this sample next. Implementation of each idea is left up to you.

### A HTTP Based API

The `FrontEnd` in this sample is a dummy that automatically generates work. A real application could for example use [Akka HTTP](http://doc.akka.io/docs/akka-http/current/scala/http/introduction.html) to provide a HTTP REST (or other) API for external clients.

### Scaling better with many masters

If the singleton master becomes a bottleneck we could start several master actors and shard the jobs among them. This could be achieved by using [Akka Cluster Sharding](http://doc.akka.io/docs/akka/current/scala/cluster-sharding.html) with many `WorkManager` actors as entities and a hash of some sort on the payload deciding which master it should go to.

### More tools for building distributed systems

In this example we have used
[Cluster Singleton](http://doc.akka.io/docs/akka/current/scala/cluster-singleton.html#cluster-singleton)
and
[Distributed Publish Subscribe](http://doc.akka.io/docs/akka/current/scala/distributed-pub-sub.html)
 but those are not the only tools in Akka Cluster.

 You can also find a good overview of the various modules that make up Akka in
 [this section of the official documentation](http://doc.akka.io/docs/akka/current/scala/guide/modules.html#cluster-singleton)
