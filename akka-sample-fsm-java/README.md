## Finite State Machine in Actors

This sample is an adaptation of [Dining Hakkers](http://www.dalnefre.com/wp/2010/08/dining-philosophers-in-humus/). 

Open [DiningHakkersTyped.scala](src/main/java/sample/DiningHakkers.java).

It illustrates how the behaviors and transitions can be defined with Akka Typed.

Start the application by typing `mvn -Dexec.mainClass="sample.DiningHakkers" exec:java`. In the log output you can see the actions of the `Hakker` actors.

Read more about Akka Typed in [the documentation](http://doc.akka.io/docs/akka/2.6/).
