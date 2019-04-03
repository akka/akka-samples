import com.typesafe.sbt.SbtMultiJvm.multiJvmSettings
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

val AkkaVersion = "2.5.22"
val AkkaAddOnsVersion = "1.1.0"
val AkkaPersistenceCassandraVersion = "0.91"
val AkkaHttpVersion = "10.1.4"
val AkkaClusterManagementVersion = "0.17.0"

lazy val `akka-sample-cqrs-scala` = project
  .in(file("."))
  .settings(multiJvmSettings: _*)
  .settings(
    organization := "com.typesafe.akka.samples",
    scalaVersion := "2.12.7",
    scalacOptions in Compile ++= Seq("-deprecation", "-feature", "-unchecked", "-Xlog-reflective-calls", "-Xlint"),
    javacOptions in Compile ++= Seq("-Xlint:unchecked", "-Xlint:deprecation"),
    javaOptions in run ++= Seq("-Xms128m", "-Xmx1024m"),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
      "com.typesafe.akka" %% "akka-remote" % AkkaVersion,
      "com.typesafe.akka" %% "akka-cluster" % AkkaVersion,
      "com.typesafe.akka" %% "akka-cluster-sharding" % AkkaVersion,
      "com.typesafe.akka" %% "akka-persistence" % AkkaVersion,
      "com.typesafe.akka" %% "akka-persistence-query" % AkkaVersion,
      "com.typesafe.akka" %% "akka-persistence-cassandra" % AkkaPersistenceCassandraVersion,
      "com.typesafe.akka" %% "akka-persistence-cassandra-launcher" % AkkaPersistenceCassandraVersion,
      "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "com.typesafe.akka" %% "akka-multi-node-testkit" % AkkaVersion % "test",
      "org.scalatest" %% "scalatest" % "3.0.5" % Test),
    fork in run := false,
    mainClass in (Compile, run) := Some("sample.cqrs.CqrsApp"),
    // disable parallel tests
    parallelExecution in Test := false,
    // add aliases to start up a write model (wmodel1) and read model instances (rmodel1, rmodel2, rmodel3)
    addCommandAlias("wmodel", "runMain sample.cqrs.CqrsApp 2551 -Dakka.cluster.roles.0=write-model"),
    addCommandAlias("rmodel1", "runMain sample.cqrs.CqrsApp 2552 -Dakka.cluster.roles.0=read-model"),
    addCommandAlias("rmodel2", "runMain sample.cqrs.CqrsApp 2553 -Dakka.cluster.roles.0=read-model"),
    addCommandAlias("rmodel3", "runMain sample.cqrs.CqrsApp 2554 -Dakka.cluster.roles.0=read-model"),
    addCommandAlias("rmodel4", "runMain sample.cqrs.CqrsApp 2555 -Dakka.cluster.roles.0=read-model"),
    licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0")))
  )
  .configs (MultiJvm)
