organization := "com.lightbend.akka.samples"
name := "akka-sample-fsm-scala"

val AkkaVersion = "2.8.3"
val LogbackClassicVersion = "1.2.11"
val AkkaDiagnosticsVersion = "2.0.0"

scalaVersion := "2.13.10"

resolvers += "Akka library repository".at("https://repo.akka.io/maven")

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
  "ch.qos.logback" % "logback-classic" % LogbackClassicVersion,
  "com.lightbend.akka" %% "akka-diagnostics" % AkkaDiagnosticsVersion
)

run / fork := true

licenses := Seq(
  ("CC0", url("http://creativecommons.org/publicdomain/zero/1.0"))
)
