lazy val `akka-sample-camel-java` = project
  .enablePlugins(AkkaSamplePlugin)
  .settings(
    name        := "Akka Camel with Java",
    baseProject := "akka-sample-camel-java"
  )

lazy val `akka-sample-camel-scala` = project
  .enablePlugins(AkkaSamplePlugin)
  .settings(
    name        := "Akka Camel with Scala",
    baseProject := "akka-sample-camel-scala"
  )

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

lazy val `akka-sample-multi-node-scala` = project
  .enablePlugins(AkkaSamplePlugin)
  .settings(
    name        := "Akka Multi Node with Scala",
    baseProject := "akka-sample-multi-node-scala"
  )

lazy val `akka-sample-osgi-dining-hakkers` = project
  .enablePlugins(AkkaSamplePlugin)
  .settings(
    name        := "Akka OSGi Dining Hakkers",
    baseProject := "akka-sample-osgi-dining-hakkers"
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

lazy val `akka-sample-supervision-java` = project
  .enablePlugins(AkkaSamplePlugin)
  .settings(
    name        := "Akka Supervision with Java",
    baseProject := "akka-sample-supervision-java"
  )
