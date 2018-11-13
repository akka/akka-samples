## Finite State Machine in Actors

This sample is an adaptation of [Dining Hakkers](http://www.dalnefre.com/wp/2010/08/dining-philosophers-in-humus/). It illustrates how state and behavior can be managed within an Actor with two different approaches; using `become` and using the `FSM` trait. The sample also contains an implementation of a simple redelivering actor implemented as a FSM.

## Dining Hakkers with Become

Open [DiningHakkersOnBecome.scala](src/main/scala/sample/become/DiningHakkersOnBecome.scala).

It illustrates how current behavior can be replaced with `context.become`. Note that no `var` members are used, instead the state is encoded in the current behavior and its parameters.

Start the application by typing `sbt "runMain sample.become.DiningHakkersOnBecome"`. In the log output you can see the actions of the `Hakker` actors.

Read more about `become` in [the documentation](http://doc.akka.io/docs/akka/2.5/scala/actors.html#Become_Unbecome).

## Dining Hakkers with FSM

Open [DiningHakkersOnFsm.scala](src/main/scala/sample/fsm/DiningHakkersOnFsm.scala).

It illustrates how the states and transitions can be defined with the `akka.actor.FSM` trait.

Start the application by typing `sbt "runMain sample.fsm.DiningHakkersOnFsm"`. In the log output you can see the actions of the `Hakker` actors.

Read more about `akka.actor.FSM` in [the documentation](http://doc.akka.io/docs/akka/2.5/scala/fsm.html).

## Simple redelivering FSM

Open [FsmSimpleRedelivery.scala](src/main/scala/sample/redelivery/FsmSimpleRedelivery.scala).

It illustrates how you can take care of message redelivery between two or more sides. This implementation is able to process only one message at a time.

Start the application by typing `sbt "runMain sample.redelivery.FsmSimpleRedelivery"`. In the log output you can see the actions of the `Requester` and the `Receiver` actors.

## Dining Hakkers with Akka Typed

Open [DiningHakkersTyped.scala](src/main/scala/sample/typed/DiningHakkersTyped.scala).

It illustrates how the behaviors and transitions can be defined with Akka Typed.

Start the application by typing `sbt "runMain sample.typed.DiningHakkersTyped"`. In the log output you can see the actions of the `Hakker` actors.

Read more about Akka Typed in [the documentation](http://doc.akka.io/docs/akka/current/typed).

