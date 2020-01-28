organization := "com.lightbend.akka.samples"
name := "akka-sample-fsm-java"

val akkaVersion = "2.6.3"

scalaVersion := "2.12.8"
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "ch.qos.logback" % "logback-classic" % "1.2.3")

licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0")))
