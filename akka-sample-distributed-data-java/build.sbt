import com.typesafe.sbt.SbtMultiJvm.multiJvmSettings
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

val AkkaVersion = "2.7.0"
val AkkaDiagnosticsVersion = "2.0.0-M3"
val LogbackClassicVersion = "1.2.11" 
val ScalaTestVersion = "3.0.8"

val `akka-sample-distributed-data-java` = project
  .in(file("."))
  .settings(multiJvmSettings: _*)
  .settings(
    organization := "com.lightbend.akka.samples",
    version := "1.0",
    scalaVersion := "2.13.10",
    Compile / scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked", "-Xlog-reflective-calls", "-Xlint"),
    Compile / javacOptions ++= Seq("-parameters", "-Xlint:unchecked", "-Xlint:deprecation", "-Xdiags:verbose"),
    javaOptions in run ++= Seq("-Xms128m", "-Xmx1024m"),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-cluster-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,
      "com.lightbend.akka" %% "akka-diagnostics" % AkkaDiagnostics,
      "com.typesafe.akka" %% "akka-multi-node-testkit" % AkkaVersion % Test,
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
      "ch.qos.logback" % "logback-classic" % LogbackClassicVersion % Test,
      "org.scalatest" %% "scalatest" % ScalaTestVersion % Test),
    fork in run := true,
    Global / cancelable := false, // ctrl-c
    // disable parallel tests
    parallelExecution in Test := false,
    // show full stack traces and test case durations
    testOptions in Test += Tests.Argument("-oDF"),
    logBuffered in Test := false,
    licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0")))
  )
  .configs (MultiJvm)
