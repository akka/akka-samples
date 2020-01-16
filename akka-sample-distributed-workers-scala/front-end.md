# Front-End Nodes

Typically in systems built with Akka, clients submit requests using a RESTful API or a gRPC API. 
Either [Akka HTTP](http://doc.akka.io/docs/akka-http/current/scala/http/introduction.html) or [Play Framework](https://www.playframework.com) 
are great choices for implementing an HTTP API for the front-end, [Akka
gRPC](https://doc.akka.io/docs/akka-grpc/current/index.html) can be used of a gRPC front end is preferred.

To limit the scope of this example, we have chosen to emulate client activity with two ordinary actors:

* The `FrontEnd` actor generates payloads at random intervals and sends them to the 'Master' actor.
* The `WorkResultConsumerActor` that consumes results and logs them.


The `FrontEnd` actor only concerns itself with posting workloads, and does not care when the work has been completed. 
When a workload has been processed successfully and passed to the `Master` actor it publishes the result to all interested cluster nodes through Distributed Pub-Sub. 

The `WorkResultConsumerActor` subscribes to the completion events and logs when a workload has completed.

Now, let's take a look at the code that accomplishes this front-end behavior.

## The Front-end Actor

@@snip [FrontEnd.scala]($g8src$/scala/worker/FrontEnd.scala) { #front-end }

Note in the source code that as the 'FrontEnd' actor starts up, it:

1. Schedules 'Tick' messages to itself.
1. Each 'Tick' message:
    1. Triggers creation of a new 'Work' message.
    1. Switches to a new 'busy' behavior.
    1. Sends the 'Work' message to the 'Master' actor of a 'back-end' node.

As you can see the `FrontEnd` actor schedules `Tick` messages to itself when starting up. the `Tick` message then triggers creation of a new `Work`, sending the work to the `Master` actor on a `back-end` node and switching to a new `busy` behavior.

The cluster contains one `Master` actor. The `FrontEnd` actor does not need to know the exact location because it sends work to the `masterProxy` that is a cluster singleton proxy.

The 'Master' actor can accept or deny a work request and we need to deal with unexpected errors:

* If the 'Master' accepts the request, the actor schedules a new tick to itself and toggles back to the idle behavior.
* To deal with failures, the message uses the [ask pattern](http://doc.akka.io/docs/akka/current/scala/actors.html#ask-send-and-receive-future) to add a timeout to wait for a reply. If the timeout expires before the master responds, the returned 'Future' fails with an akka.pattern.AskTimeoutException.
* We transform timeouts or denials from the 'Master' into a 'Failed' value that is automatically piped back to `self` and a `Retry` is scheduled.

When a workload has been acknowledged by the master, the actor goes back to the  `idle` behavior which schedules
a `Tick` to start the process again.  

If the work is not accepted or there is no response, for example if the message or response got lost, the `FrontEnd` actor backs off a bit and then sends the work again.

You can see the how the actors on a front-end node is started in the method `Main.start` when the node
contains the `front-end` role:

@@snip [Main.scala]($g8src$/scala/worker/Main.scala) { #front-end }

## The Work Result Consumer Actor
As mentioned in the introduction, results are published using Distributed Pub-Sub. The 'WorkResultConsumerActor' subscribes to completion events and logs when a workload has completed:

@@snip [Main.scala]($g8src$/scala/worker/WorkResultConsumer.scala) { #work-result-consumer }

In an actual application you would probably want a way for clients to poll or stream the status changes of the submitted work.
