This tutorial contains examples that illustrate a subset of[Akka Persistence](http://doc.akka.io/docs/akka/2.4.14/scala/persistence.html) features.

- persistent actor
- persistent actor snapshots
- persistent actor recovery
- persistent actor views

Custom storage locations for the journal and snapshots can be defined in [application.conf](src/main/resources/application.conf).

## Persistent actor

[PersistentActorExample.scala](src/main/scala/sample/persistence/PersistentActorExample.scala) is described in detail in the [Event sourcing](http://doc.akka.io/docs/akka/2.4.14/scala/persistence.html#event-sourcing) section of the user documentation. With every application run, the `ExamplePersistentActor` is recovered from events stored in previous application runs, processes new commands, stores new events and snapshots and prints the current persistent actor state to `stdout`.

To run this example, type `sbt "runMain sample.persistence.PersistentActorExample"`.

## Persistent actor snapshots

[SnapshotExample.scala](src/main/scala/sample/persistence/SnapshotExample.scala) demonstrates how persistent actors can take snapshots of application state and recover from previously stored snapshots. Snapshots are offered to persistent actors at the beginning of recovery, before any messages (younger than the snapshot) are replayed.

To run this example, type `sbt "runMain sample.persistence.SnapshotExample"`. With every run, the state offered by the most recent snapshot is printed to `stdout`, followed by the updated state after sending new persistent messages to the persistent actor.

## Persistent actor recovery

[PersistentActorFailureExample.scala](src/main/scala/sample/persistence/PersistentActorFailureExample.scala) shows how a persistent actor can throw an exception, restart and restore the state by replaying the events.

To run this example, type `sbt "runMain sample.persistence.PersistentActorFailureExample"`.

## Persistent actor views

[ViewExample.scala](src/main/scala/sample/persistence/ViewExample.scala) demonstrates how a view (`ExampleView`) is updated with the persistent message stream of a persistent actor (`ExamplePersistentActor`). Messages sent to the persistent actor are scheduled periodically. Views also support snapshotting to reduce recovery time.

To run this example, type `sbt "runMain sample.persistence.ViewExample"`.

