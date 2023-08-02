This example illustrates durable state storage with [Akka Persistence](https://doc.akka.io/docs/akka/current/typed/index-persistence-durable-state.html).

Study the source code of the [ShoppingCart.scala](src/main/java/sample/persistence/ShoppingCart.java). A few things
to note:

* The actor is implemented with the `DurableStateBehavior`
* It defines `Command` and `State`
* Commands define `replyTo: ActorRef` to send a confirmation when the state has been successfully persisted

Tests are defined in [ShoppingCartTest.java](src/test/java/sample/persistence/ShoppingCartTest.java).
To run the tests, enter:

```
mvn test
```
