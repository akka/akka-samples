val AkkaVersion = "2.6-SNAPSHOT"
val AlpakkaKafkaVersion = "1.1.0"
val AkkaManagementVersion = "1.0.5"

lazy val `akka-sample-kafka-to-sharding` = project
  .in(file("."))
  .settings(
    organization := "com.lightbend.akka.samples",
    version := "1.0",
    scalaVersion := "2.12.8",
    scalacOptions in Compile ++= Seq("-deprecation", "-feature", "-unchecked", "-Xlog-reflective-calls", "-Xlint"),
    javacOptions in Compile ++= Seq("-Xlint:unchecked", "-Xlint:deprecation"),
    libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-stream-kafka" % AlpakkaKafkaVersion,
        "com.typesafe.akka" %% "akka-cluster-sharding-typed" % AkkaVersion,
        "com.typesafe.akka" %% "akka-stream-typed" % AkkaVersion,
        "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,
        "com.lightbend.akka.management" %% "akka-management" % AkkaManagementVersion,
        "com.lightbend.akka.management" %% "akka-management-cluster-http" % AkkaManagementVersion,
        "ch.qos.logback" % "logback-classic" % "1.2.3",
        "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
        "org.scalatest" %% "scalatest" % "3.0.8" % Test),
    Global / cancelable := false, // ctrl-c
    // show full stack traces and test case durations
    testOptions in Test += Tests.Argument("-oDF"),
    licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0"))))
