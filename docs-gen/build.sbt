lazy val `akka-sample-cluster-java` = project
  .enablePlugins(AkkaSamplePlugin)
  .settings(
    name        := "Akka Cluster with Java",
    baseProject := "akka-sample-cluster-java"
  )

lazy val `akka-sample-cluster-scala` = project
  .enablePlugins(AkkaSamplePlugin)
  .settings(
    name        := "Akka Cluster with Scala",
    baseProject := "akka-sample-cluster-scala"
  )

lazy val `akka-sample-distributed-data-java` = project
  .enablePlugins(AkkaSamplePlugin)
  .settings(
    name        := "Akka Distributed Data with Java",
    baseProject := "akka-sample-distributed-data-java"
  )

lazy val `akka-sample-distributed-data-scala` = project
  .enablePlugins(AkkaSamplePlugin)
  .settings(
    name        := "Akka Distributed Data with Scala",
    baseProject := "akka-sample-distributed-data-scala"
  )

lazy val `akka-sample-distributed-workers-scala` = project
  .enablePlugins(AkkaSamplePlugin)
  .settings(
    name        := "Akka Distributed Workers with Scala",
    baseProject := "akka-sample-distributed-workers-scala"
  )

lazy val `akka-sample-fsm-java` = project
  .enablePlugins(AkkaSamplePlugin)
  .settings(
    name        := "Akka FSM with Java",
    baseProject := "akka-sample-fsm-java"
  )

lazy val `akka-sample-fsm-scala` = project
  .enablePlugins(AkkaSamplePlugin)
  .settings(
    name        := "Akka FSM with Scala",
    baseProject := "akka-sample-fsm-scala"
  )

lazy val `akka-sample-persistence-java` = project
  .enablePlugins(AkkaSamplePlugin)
  .settings(
    name        := "Akka Persistence with Java",
    baseProject := "akka-sample-persistence-java"
  )

lazy val `akka-sample-persistence-scala` = project
  .enablePlugins(AkkaSamplePlugin)
  .settings(
    name        := "Akka Persistence with Scala",
    baseProject := "akka-sample-persistence-scala"
  )

lazy val `akka-sample-sharding-java` = project
  .enablePlugins(AkkaSamplePlugin)
  .settings(
    name        := "Akka Cluster Sharding with Java",
    baseProject := "akka-sample-sharding-java"
  )

lazy val `akka-sample-sharding-scala` = project
  .enablePlugins(AkkaSamplePlugin)
  .settings(
    name        := "Akka Cluster Sharding with Scala",
    baseProject := "akka-sample-sharding-scala"
  )

lazy val `akka-sample-kafka-to-sharding-scala` = project
  .enablePlugins(AkkaSamplePlugin)
  .settings(
    name        := "Akka Kafka to Sharding with Scala",
    baseProject := "akka-sample-kafka-to-sharding-scala"
  )
