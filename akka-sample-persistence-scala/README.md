This example illustrates event sourcing with [Akka Persistence](https://doc.akka.io/docs/akka/2.6/typed/persistence.html).

Study the source code of the [ShoppingCart.scala](src/main/scala/sample/persistence/ShoppingCart.scala). A few things
to note:

* The actor is implemented with the `EventSourcedBehavior`
* It defines `Command`, `Event` and `State`
* Commands define `replyTo: ActorRef` to send a confirmation when the event has been successfully persisted
* `State` is only updated in the event handler
* `withRetention` to enable [snapshotting](https://doc.akka.io/docs/akka/2.6/typed/persistence-snapshot.html)
* `onPersistFailure` defines restarts with backoff in case of failures

Tests are defined in [ShoppingCartSpec.scala](src/test/scala/sample/persistence/ShoppingCartSpec.scala).
To run the tests, enter:

```
sbt
sbt:akka-sample-persistence-scala> test
```

The `ShoppingCart` application is expanded further in the `akka-sample-cqrs-scala` sample. In that sample the events are tagged to be consumed by even processors to build other representations from the events, or publish the events to other services.
