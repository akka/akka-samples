organization := "com.lightbend.akka.samples"
name := "akka-sample-persistence-scala"

scalaVersion := "2.13.10"
val AkkaVersion = "2.7.0"
val AkkaDiagnosticsVersion = "2.0.0-M3"
val LogbackClassicVersion = "1.2.11" 
val ScalaTestVersion = "3.1.0"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-persistence-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,
  "com.lightbend.akka" %% "akka-diagnostics" % AkkaDiagnosticsVersion,
  "ch.qos.logback" % "logback-classic" % LogbackClassicVersion,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
  "org.scalatest" %% "scalatest" % ScalaTestVersion % Test
)

scalacOptions in Compile ++= Seq("-deprecation", "-feature", "-unchecked", "-Xlog-reflective-calls", "-Xlint")

// show full stack traces and test case durations
testOptions in Test += Tests.Argument("-oDF")
logBuffered in Test := false

licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0")))
