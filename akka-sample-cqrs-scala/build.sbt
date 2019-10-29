val AkkaVersion = "2.6.0-RC2"
val AkkaPersistenceCassandraVersion = "0.100"
val AkkaHttpVersion = "10.1.10"
val AkkaClusterManagementVersion = "1.0.3"

lazy val `akka-sample-cqrs-scala` = project
  .in(file("."))
  .settings(
    organization := "com.typesafe.akka.samples",
    scalaVersion := "2.13.1",
    scalacOptions in Compile ++= Seq("-deprecation", "-feature", "-unchecked", "-Xlog-reflective-calls", "-Xlint"),
    javacOptions in Compile ++= Seq("-Xlint:unchecked", "-Xlint:deprecation"),
    javaOptions in run ++= Seq("-Xms128m", "-Xmx1024m"),
    libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-cluster-sharding-typed" % AkkaVersion,
        "com.typesafe.akka" %% "akka-persistence-typed" % AkkaVersion,
        "com.typesafe.akka" %% "akka-persistence-query" % AkkaVersion,
        "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,
        "com.typesafe.akka" %% "akka-persistence-cassandra" % AkkaPersistenceCassandraVersion,
        "com.typesafe.akka" %% "akka-persistence-cassandra-launcher" % AkkaPersistenceCassandraVersion,
        "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
        "ch.qos.logback" % "logback-classic" % "1.2.3",
        "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
        "org.scalatest" %% "scalatest" % "3.0.8" % Test,
        "commons-io" % "commons-io" % "2.4" % Test),
    fork in run := false,
    mainClass in (Compile, run) := Some("sample.cqrs.Main"),
    // disable parallel tests
    parallelExecution in Test := false,
    // add aliases to start up a write model (wmodel1) and read model instances (rmodel1, rmodel2, rmodel3)
    addCommandAlias("wmodel1", "runMain sample.cqrs.Main 2551 -Dakka.cluster.roles.0=write-model"),
    addCommandAlias("rmodel1", "runMain sample.cqrs.Main 2552 -Dakka.cluster.roles.0=read-model"),
    addCommandAlias("wmodel2", "runMain sample.cqrs.Main 2553 -Dakka.cluster.roles.0=write-model"),
    addCommandAlias("rmodel2", "runMain sample.cqrs.Main 2554 -Dakka.cluster.roles.0=read-model"),
    licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0"))))
