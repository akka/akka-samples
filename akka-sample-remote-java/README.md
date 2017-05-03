In order to showcase the [remote capabilities of Akka](http://doc.akka.io/docs/akka/2.5/java/remoting.html) we thought a remote calculator could do the trick. This sample demonstrates both remote deployment and look-up of remote actors.

## Lookup Remote Actors

This sample involves two actor systems.

- **CalculatorSystem** listens on port 2552 and starts one actor, theCalculatorActorthat provides a service for arithmetic operations.
- **LookupSystem** listens on port 2553 and starts one actor, theLookupActorthat sends operations to the remote calculator service.

Open [LookupApplication.java](src/main/java/sample/remote/calculator/LookupApplication.java).

There you see how the two actor systems and actors are started. In this first step they are running in the same JVM process, but you can run them in separate processes as described later. Note that this changes nothing in the configuration or implementation.

The two actor systems use different configuration, which is where the listen port is defined. The CalculatorSystem uses [calculator.conf](src/main/resources/calculator.conf) and the LookupSystem uses [remotelookup.conf](src/main/resources/remotelookup.conf).

Note that the configuration files also import the [common.conf](src/main/resources/common.conf). This enables the remoting by installing the RemoteActorRefProvider and chooses the default remote transport. Be sure to replace the default IP 127.0.0.1 with the real address the system is reachable by if you deploy onto multiple machines!

The [CalculatorActor](src/main/java/sample/remote/calculator/CalculatorActor.java) does not illustrate anything exciting. More interesting is the [LookupActor](src/main/java/sample/remote/calculator/LookupActor.java). It takes a String `path` as constructor parameter. This is the full path, including the remote address of the calculator service. Observe how the actor system name of the path matches the remote system’s name, as do IP and port number. Top-level actors are always created below the "/user" guardian, which supervises them.

    "akka.tcp://CalculatorSystem@127.0.0.1:2552/user/calculator"

First it sends an `Identify` message to the actor selection of the path. The remote calculator actor will reply with `ActorIdentity` containing its `ActorRef`. `Identify` is a built-in message that all Actors will understand and automatically reply to with an `ActorIdentity`. If the identification fails it will be retried after the scheduled timeout by the `LookupActor`.

Note how none of the code is specific to remoting, this also applies when talking to a local actor which might terminate and be recreated. That is what we call Location Transparency.

Once it has the `ActorRef` of the remote service it can `watch` it. The remote system might be shutdown and later started up again, then `Terminated` is received on the watching side and it can retry the identification to establish a connection to the new remote system.

## Run the Lookup Sample

To run this sample, type `sbt "runMain sample.remote.calculator.LookupApplication"`.

In the log output you should see something like:

    Started LookupSystem
    Calculating 74 - 42
    Sub result: 74 - 42 = 32
    Calculating 15 + 71
    Add result: 15 + 71 = 86

The two actor systems are running in the same JVM process. It can be more interesting to run them in separate processes. Stop the application and then open two terminal windows.

Start the CalculatorSystem in the first terminal window with the following command:

    sbt "runMain sample.remote.calculator.LookupApplication Calculator"

Start the LookupSystem in the second terminal window with the following command:

    sbt "runMain sample.remote.calculator.LookupApplication Lookup"

Thereafter you can try to shutdown the CalculatorSystem in the first terminal window with 'ctrl-c' and then start it again. In the second terminal window you should see the failure detection and then how the successful calculation results are logged again when it has established a connection to the new system.

## Create Remote Actors

This sample involves two actor systems.

- **CalculatorWorkerSystem** listens on port 2552
- **CreationSystem** listens on port 2554 and starts one actor, the CreationActor that creates remote calculator worker actors in the CalculatorWorkerSystem and sends operations to them.

Open [CreationApplication.java](src/main/java/sample/remote/calculator/CreationApplication.java).

There you see how the two actor systems and actors are started. In this first step they are running in the same JVM process, but you can run them in separate processes as described later.

The two actor systems use different configuration, which is where the listen port is defined. The CalculatorWorkerSystem uses [calculator.conf](src/main/resources/calculator.conf) and the CreationSystem uses [remotecreation.conf](src/main/resources/remotecreation.conf).

Note that the configuration files also import the [common.conf](src/main/resources/common.conf). This enables the remoting by installing the RemoteActorRefProvider and chooses the default remote transport. Be sure to replace the default IP 127.0.0.1 with the real address the system is reachable by if you deploy onto multiple machines!

The [CreationActor](src/main/java/sample/remote/calculator/CreationActor.java) creates a child [CalculatorActor](src/main/java/sample/remote/calculator/CalculatorActor.java) for each incoming `MathOp` message. The configuration contains a deployment section that matches these child actors and defines that the actors are to be deployed at the remote system. The wildcard (*) is needed because the child actors are created with unique anonymous names.

    akka.actor.deployment {
      /creationActor/* {
        remote = "akka.tcp://CalculatorWorkerSystem@127.0.0.1:2552"
      }
    }

Error handling, i.e. supervision, works exactly in the same way as if the child actor was a local child actor. In addtion, in case of network failures or JVM crash the child will be terminated and automatically removed from the parent even though they are located on different machines.

## Run the Creation Sample

To run this sample, type `sbt "runMain sample.remote.calculator.CreationApplication"`.

In the log output you should see something like:

    Started CreationSystem
    Calculating 7135 / 62
    Div result: 7135 / 62 = 115.08
    Calculating 0 * 9
    Mul result: 0 * 9 = 0

The two actor systems are running in the same JVM process. It can be more interesting to run them in separate processes. Stop the application and then open two terminal windows.


Start the CalculatorWorkerSystem in the first terminal window with the following command:
    sbt "runMain sample.remote.calculator.CreationApplication CalculatorWorker"

Start the CreationSystem in the second terminal window with the following command (on one line):

    <path to activator dir>/activator 
      "run-main sample.remote.calculator.CreationApplication Creation"

Thereafter you can try to shutdown the CalculatorWorkerSystem in the first terminal window with 'ctrl-c' and then start it again. In the second terminal window you should see the failure detection and then how the successful calculation results are logged again when it has established a connection to the new system.
