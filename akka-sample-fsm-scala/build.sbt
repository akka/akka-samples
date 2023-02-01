organization := "com.lightbend.akka.samples"
name := "akka-sample-fsm-scala"

val AkkaVersion = "2.7.0"
val LogbackClassicVersion = "1.2.11"
val AkkaDiagnosticsVersion = "2.0.0-M3"

scalaVersion := "2.13.10"
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
  "ch.qos.logback" % "logback-classic" % LogbackClassicVersion,
  "com.lightbend.akka" %% "akka-diagnostics" % AkkaDiagnostics
)

licenses := Seq(
  ("CC0", url("http://creativecommons.org/publicdomain/zero/1.0"))
)
