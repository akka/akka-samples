## Finite State Machine in Actors

This sample is an adaptation of [Dining Hakkers](http://www.dalnefre.com/wp/2010/08/dining-philosophers-in-humus/). It illustrates how state and behavior can be managed within an Actor with two different approaches; using `become` and using the `AbstractFSM` class.

## Dining Hakkers with Become

Open [DiningHakkersOnBecome.java](src/main/java/sample/become/DiningHakkersOnBecome.java).

It illustrates how current behavior can be replaced with `context.become`. Note that no `var` members are used, instead the state is encoded in the current behavior and its parameters.

Start the application by typing `sbt "runMain sample.become.DiningHakkersOnBecome"` or `mvn compile exec:java -Dexec.mainClass="sample.become.DiningHakkersOnBecome"`. In the log output you can see the actions of the `Hakker` actors.

Read more about `become` in [the documentation](http://doc.akka.io/docs/akka/2.5/java/actors.html#Become_Unbecome).

## Dining Hakkers with FSM

Open [DiningHakkersOnFsm.java](src/main/java/sample/fsm/DiningHakkersOnFsm.java).

It illustrates how the states and transitions can be defined with the `akka.actor.AbstractFSM` class.

Start the application by typing `sbt "runMain sample.fsm.DiningHakkersOnFsm"` or `mvn compile exec:java -Dexec.mainClass="sample.fsm.DiningHakkersOnFsm"`. In the log output you can see the actions of the `Hakker` actors.

Read more about `akka.actor.FSM` in [the documentation](http://doc.akka.io/docs/akka/2.5/java/fsm.html).

## Dining Hakkers with Akka Typed

Open [DiningHakkersTyped.java](src/main/java/sample/typed/DinningHakkersTyped.java).

It illustrates how the behaviors and transitions can be defined with Akka Typed.

Start the application by typing `sbt "runMain sample.typed.DiningHakkersTyped"`. In the log output you can see the actions of the `Hakker` actors.

Read more about Akka Typed in [the documentation](http://doc.akka.io/docs/akka/current/typed).

