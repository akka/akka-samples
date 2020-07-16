name := "akka-distributed-workers"

version := "1.0"

scalaVersion := "2.13.1"
val akkaVersion = "2.6.8"

val cassandraPluginVersion = "0.103"

Global / cancelable := false

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-cluster-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-query" % akkaVersion,
  "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-cassandra" % cassandraPluginVersion,
  // this allows us to start cassandra from the sample
  "com.typesafe.akka" %% "akka-persistence-cassandra-launcher" % cassandraPluginVersion,
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  // test dependencies
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
  "org.scalatest" %% "scalatest" % "3.0.8" % Test,
  "commons-io" % "commons-io" % "2.4" % Test)
