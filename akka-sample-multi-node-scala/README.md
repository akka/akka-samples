This sample contains [sbt](http://www.scala-sbt.org/) build settings and test classes for illustrating multi-node testing with Akka.

Please refer to the full documentation of [multi-node testing](http://doc.akka.io/docs/akka/2.5/dev/multi-node-testing.html) and the closely related [multi-jvm testing](http://doc.akka.io/docs/akka/2.5/dev/multi-jvm-testing.html) for details. There is also an section on [cluster testing](http://doc.akka.io/docs/akka/2.5/scala/cluster-usage.html#How_to_Test).

## sbt setup

Open [project/plugins.sbt](project/plugins.sbt).

It adds the [sbt-multi-jvm](http://github.com/sbt/sbt-multi-jvm) plugin to the build.

Open [build.sbt](build.sbt).

It includes the MultiJvm settings that are needed to run multi-jvm tests.

## Tests

Open [MultiNodeSample.scala](src/multi-jvm/scala/sample/multinode/MultiNodeSample.scala).

Note that MultiJvm test sources are located in `src/multi-jvm/...` and the test classes must end with `MultiJvm` followed by the node name, typically`Node1`, `Node2`, `Node3`...

To hook up the MultiNodeSpec with with ScalaTest you need something like [STMultiNodeSpec.scala](src/test/scala/sample/multinode/STMultiNodeSpec.scala).

To see the test in action, type the following `sbt multi-jvm:test`.

In case you have many tests in the project it can be convenient to run a single test from the sbt prompt:

    > multi-jvm:testOnly sample.multinode.MultiNodeSampleSpec

The same test can be run on multiple machines as described in the [multi-node testing documentation](http://doc.akka.io/docs/akka/2.5/dev/multi-node-testing.html).

