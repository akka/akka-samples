This example illustrates event sourcing with [Akka Persistence](https://doc.akka.io/docs/akka/2.6/typed/persistence.html).

Study the source code of the [ShoppingCart.scala](src/main/java/sample/persistence/ShoppingCart.java). A few things
to note:

* The actor is implemented with the `EventSourcedBehavior`
* It defines `Command`, `Event` and `State`
* Commands define `replyTo: ActorRef` to send a confirmation when the event has been successfully persisted
* `State` is only updated in the event handler
* `withRetention` to enable [snapshotting](https://doc.akka.io/docs/akka/2.6/typed/persistence-snapshot.html)
* `onPersistFailure` defines restarts with backoff in case of failures

Tests are defined in [ShoppingCartTest.java](src/test/java/sample/persistence/ShoppingCartTest.java).
To run the tests, enter:

```
mvn test
```

The `ShoppingCart` application is expanded further in the `akka-sample-cqrs-java` sample. In that sample the events are tagged to be consumed by even processors to build other representations from the events, or publish the events to other services.

---

The Akka family of projects is managed by teams at Lightbend with help from the community.

License
-------

Akka is licensed under the Business Source License 1.1, please see the [Akka License FAQ](https://www.lightbend.com/akka/license-faq).