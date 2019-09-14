This tutorial contains a sample template illustrating the way to use protocol-buffers serialization for akka-cluster messages

## Worker Dial-in Example

Let's take a look at an example that illustrates how workers, here named *backend*, can detect and register to new master nodes, here named *frontend*.

The example application provides a service to transform text. When some text is sent to one of the frontend services, it will be delegated to one of the backend workers, which performs the transformation job, and sends the result back to the original client. New backend nodes, as well as new frontend nodes, can be added or removed to the cluster dynamically.

Open [TransformationMessages.proto](src/main/protobuf/sample/cluster/transformation/TransformationMessages.proto). It defines the messages that are sent between the actors. And we use scala-pb to generate the class files for protocol-buffer based classes for actor messages.

The [Scalapb](https://github.com/scalapb/ScalaPB), automatically generates class files for *.proto files present inside src/main/protobuf folder.

And the messages are serialized using [ScalaPbSerializer](src/main/scala/sample/cluster/transformation/ScalaPbSerializer.scala), which need to be specified as part of the application.conf as shown below.

```
   akka {
      actor {
        provider = cluster
        allow-java-serialization = off
        serializers {
          scalapb = "sample.cluster.transformation.ScalaPbSerializer"
        }
        serialization-bindings {
          "scalapb.GeneratedMessage" = scalapb
        }
        serialization-identifiers {
          "sample.cluster.transformation.ScalaPbSerializer" = 10000
        }
      }
  }
```

The backend worker that performs the transformation job is defined in [TransformationBackend.scala](src/main/scala/sample/cluster/transformation/TransformationBackend.scala).

Note that the `TransformationBackend` actor subscribes to cluster events to detect new, potential, frontend nodes, and send them a registration message so that they know that they can use the backend worker.

The frontend that receives user jobs and delegates to one of the registered backend workers is defined in [TransformationFrontend.scala](src/main/scala/sample/cluster/transformation/TransformationFrontend.scala).

Note that the `TransformationFrontend` actor watch the registered backend to be able to remove it from its list of available backend workers. Death watch uses the cluster failure detector for nodes in the cluster, i.e. it detects network failures and JVM crashes, in addition to graceful termination of watched actor.

To run this sample, type ` sbt "runMain sample.cluster.transformation.TransformationApp"` if it is not already started.

TransformationApp starts 5 actor systems (cluster members) in the same JVM process. It can be more interesting to run them in separate processes. Stop the application and run the following commands in separate terminal windows.

    sbt "runMain sample.cluster.transformation.TransformationFrontend 2551"

    sbt "runMain sample.cluster.transformation.TransformationFrontend 2552"

    sbt "runMain sample.cluster.transformation.TransformationBackend 0"

    sbt "runMain sample.cluster.transformation.TransformationBackend 0"

    sbt "runMain sample.cluster.transformation.TransformationFrontend 0"

## Tests

Tests can be found in [src/multi-jvm](src/multi-jvm). You can run them by typing `sbt multi-jvm:test`.

