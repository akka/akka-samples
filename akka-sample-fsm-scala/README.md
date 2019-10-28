## Finite State Machine in Actors

This sample is an adaptation of [Dining Hakkers](http://www.dalnefre.com/wp/2010/08/dining-philosophers-in-humus/). 
There is no explicit support for FSMs in typed as [Behaviors can represent FSMs](https://doc.akka.io/docs/akka/2.6/typed/fsm.html).

## Dining Hakkers with Akka Typed

Open [DiningHakkersTyped.scala](src/main/scala/sample/DiningHakkers.scala).

It illustrates how the behaviors and transitions can be defined with Akka Typed.

Start the application by typing `sbt "runMain sample.DiningHakkers"`. In the log output you can see the actions of the `Hakker` actors.

Read more about Akka Typed in [the documentation](http://doc.akka.io/docs/akka/2.6/).

