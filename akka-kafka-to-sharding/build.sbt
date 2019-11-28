val AkkaVersion = "2.6-SNAPSHOT"
val AlpakkaKafkaVersion = "1.1.0"
val AkkaManagementVersion = "1.0.5"
val LogbackVersion = "1.2.3"

ThisBuild / scalaVersion := "2.12.8"
ThisBuild / organization := "com.lightbend.akka.samples"
ThisBuild / scalacOptions in Compile ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlog-reflective-calls",
  "-Xlint")
ThisBuild / javacOptions in Compile ++= Seq("-Xlint:unchecked", "-Xlint:deprecation")
ThisBuild / testOptions in Test += Tests.Argument("-oDF")
ThisBuild / licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0")))

Global / cancelable := false // ctrl-c

lazy val `akka-sample-kafka-to-sharding` = project.in(file(".")).aggregate(producer, processor)

lazy val client = project
  .in(file("client"))
  .enablePlugins(AkkaGrpcPlugin, JavaAgent)
  .settings(
    libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
        "com.typesafe.akka" %% "akka-discovery" % AkkaVersion
      ))

lazy val processor = project
  .in(file("processor"))
  .enablePlugins(AkkaGrpcPlugin, JavaAgent)
  .settings(javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.9" % "runtime;test")
  .settings(libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-stream-kafka" % AlpakkaKafkaVersion,
      "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
      "com.typesafe.akka" %% "akka-discovery" % AkkaVersion,
      "com.typesafe.akka" %% "akka-cluster-sharding-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-stream-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,
      "com.lightbend.akka.management" %% "akka-management" % AkkaManagementVersion,
      "com.lightbend.akka.management" %% "akka-management-cluster-http" % AkkaManagementVersion,
      "ch.qos.logback" % "logback-classic" % LogbackVersion,
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
      "org.scalatest" %% "scalatest" % "3.0.8" % Test))

lazy val producer = project
  .in(file("producer"))
  .settings(
    libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-stream-kafka" % AlpakkaKafkaVersion,
        "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
        "ch.qos.logback" % "logback-classic" % "1.2.3",
        "org.scalatest" %% "scalatest" % "3.0.8" % Test))
