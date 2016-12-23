## The Obligatory Hello World

Since every programming paradigm needs to solve the tough problem of printing a well-known greeting to the console we’ll introduce you to the actor-based version.

Open [HelloWorld.java](src/main/java/sample/hello/HelloWorld.java)

The `HelloWorld` actor is the application’s “main” class; when it terminates the application will shut down—more on that later. The main business logic happens in the `preStart` method, where a `Greeter` actor is created and instructed to issue that greeting we crave for. When the greeter is done it will tell us so by sending back a message, and when that message has been received it will be passed into the behavior described by the `onReceive` method where we can conclude the demonstration by stopping the `HelloWorld` actor.

## The Greeter

You will be very curious to see how the `Greeter` actor performs the actual task. Open [Greeter.java](src/main/java/sample/hello/Greeter.java).

This is extremely simple now: after its creation this actor will not do anything until someone sends it a message, and if that happens to be an invitation to greet the world then the `Greeter` complies and informs the requester that the deed has been done.

## Main class

Start the application main class `sbt "runMain sample.hello.Main"`. In the log output you can see the "Hello World!" greeting.

[Main.java](src/main/java/sample/hello/Main.java) is actually just a small wrapper around the generic launcher class `akka.Main`, which expects only one argument: the class name of the application’s main actor. This main method will then create the infrastructure needed for running the actors, start the given main actor and arrange for the whole application to shut down once the main actor terminates.

If you need more control of the startup code than what is provided by `akka.Main` you can easily write your own main class such as [Main2.java](src/main/java/sample/hello/Main2.java).

Try to run the `sample.hello.Main2` class by `sbt "runMain sample.hello.Main2"`.

## Run with Maven

This sample also includes a Maven pom.xml.

You can run the main classes with `mvn` from a terminal window using the [Exec Maven Plugin](http://mojo.codehaus.org/exec-maven-plugin/).

    mvn compile exec:java -Dexec.mainClass="akka.Main" -Dexec.args="sample.hello.HelloWorld"

    mvn compile exec:java -Dexec.mainClass="sample.hello.Main2"

