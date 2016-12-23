## The Obligatory Hello World

Since every programming paradigm needs to solve the tough problem of printing a well-known greeting to the console we’ll introduce you to the actor-based version.

Open [HelloWorld.scala](src/main/scala/sample/hello/HelloWorld.scala).

The `HelloWorld` actor is the application’s “main” class; when it terminates the application will shut down—more on that later. The main business logic happens in the `preStart` method, where a `Greeter` actor is created and instructed to issue that greeting we crave for. When the greeter is done it will tell us so by sending back a message, and when that message has been received it will be passed into the behavior described by the `receive`method where we can conclude the demonstration by stopping the `HelloWorld`actor.

## The Greeter

You will be very curious to see how the `Greeter` actor performs the actual task. Open [Greeter.scala](src/main/scala/sample/hello/Greeter.scala).

This is extremely simple now: after its creation this actor will not do anything until someone sends it a message, and if that happens to be an invitation to greet the world then the `Greeter` complies and informs the requester that the deed has been done.

## Main class

Start the application main class: `sbt "runMain sample.hello.Main"`. In the log output you can see the "Hello World!" greeting.

[Main.scala](src/main/scala/sample/hello/Main.scala) is actually just a small wrapper around the generic launcher class `akka.Main`, which expects only one argument: the class name of the application’s main actor. This main method will then create the infrastructure needed for running the actors, start the given main actor and arrange for the whole application to shut down once the main actor terminates.

If you need more control of the startup code than what is provided by `akka.Main` you can easily write your own main class such as [Main2.scala](src/main/scala/sample/hello/Main2.scala). Try to run the `sample.hello.Main2` class by running `sbt "runMain sample.hello.Main2"`.

