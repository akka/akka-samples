val AkkaVersion = "2.8.4"
val AlpakkaKafkaVersion = "4.0.2"
val AkkaManagementVersion = "1.4.0"
val AkkaHttpVersion = "10.5.2"
val EmbeddedKafkaVersion = "2.4.1.1"
val LogbackVersion = "1.2.11"

resolvers += "Akka library repository".at("https://repo.akka.io/maven")

ThisBuild / scalaVersion := "2.13.10"
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
ThisBuild / resolvers ++= Seq(
  "Akka Snapshots" at "https://repo.akka.io/snapshots",
  Resolver.bintrayRepo("akka", "snapshots")
)

run / fork := true

lazy val `akka-sample-kafka-to-sharding` = project.in(file(".")).aggregate(producer, processor, client)

lazy val kafka = project
  .in(file("kafka"))
  .settings(
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % LogbackVersion,
      "org.slf4j" % "log4j-over-slf4j" % "1.7.26",
      "io.github.embeddedkafka" %% "embedded-kafka" % EmbeddedKafkaVersion),
    cancelable := false)

lazy val client = project
  .in(file("client"))
  .enablePlugins(AkkaGrpcPlugin, JavaAgent)
  .settings(
    libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
        "com.typesafe.akka" %% "akka-discovery" % AkkaVersion))

lazy val processor = project
  .in(file("processor"))
  .enablePlugins(AkkaGrpcPlugin, JavaAgent)
  .settings(javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.9" % "runtime;test")
  .settings(libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-stream-kafka" % AlpakkaKafkaVersion,
      "com.typesafe.akka" %% "akka-stream-kafka-cluster-sharding" % AlpakkaKafkaVersion,
      "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
      "com.typesafe.akka" %% "akka-discovery" % AkkaVersion,
      "com.typesafe.akka" %% "akka-cluster-sharding-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-stream-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,
      "com.lightbend.akka.management" %% "akka-management" % AkkaManagementVersion,
      "com.lightbend.akka.management" %% "akka-management-cluster-http" % AkkaManagementVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
      "ch.qos.logback" % "logback-classic" % LogbackVersion,
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
      "org.scalatest" %% "scalatest" % "3.0.8" % Test))

lazy val producer = project
  .in(file("producer"))
  .settings(PB.targets in Compile := Seq(scalapb.gen() -> (sourceManaged in Compile).value))
  .settings(libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-stream-kafka" % AlpakkaKafkaVersion,
      "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
      "ch.qos.logback" % "logback-classic" % "1.2.11",
      "org.scalatest" %% "scalatest" % "3.0.8" % Test))
