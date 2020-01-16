# The Master Actor in Detail

The `Master` actor is without question the most involved component in this example. 
This is because it is designed to deal with failures. While the Akka cluster takes care of restarting the `Master` in case of a failure, we also want to make sure that the new `Master` can arrive at the same state as the failed `Master`. We use event sourcing and Akka Persistence to achieve this.

If the `back-end` node hosting the `Master` actor would crash the Akka Cluster Singleton makes sure it starts up on a different node, but we would also want it to reach the exact same state as the crashed node `Master`. This is achieved through use of event sourcing and [Akka Persistence](http://doc.akka.io/docs/akka/current/scala/persistence.html).

## Tracking current work items

The current set of work item is modelled in the `WorkState` class. It keeps track of the current set of work that is pending, has been accepted by a worker, has completed etc. Every change to the `WorkState` is modelled as a domain event.

This allows us to capture and store each such event that happens, we can later replay all of them on an empty model and 
arrive at the exact same state. This is how event sourcing and [Akka Persistence](http://doc.akka.io/docs/akka/current/scala/persistence.html) allows the actor to start on any node and reach the same state as a previous instance.

If the `Master` fails and is restarted, the replacement `Master` replays events from the log to retrieve the current state. This means that when the WorkState is modified, the `Master` must persist the event before acting on it. When the event is successfully stored, we can modify the state. Otherwise, if a failure occurs before the event is persisted, the replacement `Master` will not be able to attain the same state as the failed `Master`.

Let's look at how a command to process a work item from the front-end comes in:

@@snip [Master.scala]($g8src$/scala/worker/Master.scala) { #persisting }

The first thing you might notice is the comment saying _idempotent_, this means that the same work message may arrive multiple times, but regardless how many times the same work arrives, it should only be executed once. This is needed since the `FrontEnd` actor re-sends work in case of the `Work` or `Ack` messages getting lost (Akka does not provide any guarantee of delivery, [see details in the docs](http://doc.akka.io/docs/akka/current/scala/general/message-delivery-reliability.html#discussion-why-no-guaranteed-delivery-)).

To make the logic idempotent we simple check if the work id is already known, and if it is we simply `Ack` it without further logic. If the work is previously unknown, we start by transforming it into a `WorkAccepted` event, which we persist,  and only in the `EventHandler` that is called after the event has been persisted do we actually update the `workState`, and send an `Ack` back to the `FrontEnd` and trigger a search for available workers. In this case the event handler delegates the logic to the `WorkState` domain class. 

## Implementation items required for Akka Persistence

In a "normal" Actor the only thing we have only to provide a `Behavior`. For a `PersistentBehavior` 
there are three things that needs to be implemented:

 1. `persistenceId` is a global identifier for the actor, we must make sure that there is never more than one Actor instance with the same `persistenceId`  running globally, or else we would possibly mess up its journal.
 1. `commandHandler` receives incoming messages, called `Command`s and returns any Effects e.g. persisting an event
 1. `eventHandler` is invoked with the events once they have been persisted to the database

## Tracking workers

Unlike the `Master` actor, the example system contains multiple workers that can be stopped and restarted frequently. 
We do not need to save their state since the `Master` is tracking work and will simply send work to another worker 
if the original fails to respond. So, rather than persisting a list of available workers, the example uses the following 
strategy:

* Running workers periodically register with the master using a `RegisterWorker` message. 
  If a `back-end` node fails and the `Master` is started on a new node, the registrations go automatically to the new node.
* Any type of failure -- whether from the network, worker actor, or node -- that prevents a `RegisterWorker` 
  message from arriving within the `work-timeout` period causes the 'Master' actor to remove the worker from its list.

When stopping a `Worker` Actor still tries to gracefully remove it self using the `DeRegisterWorker` message, 
but in case of crash it will have no chance to communicate that with the master node.

Now let's move on to the last piece of the puzzle, the worker nodes.