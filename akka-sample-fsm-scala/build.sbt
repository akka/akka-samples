organization := "com.lightbend.akka.samples"
name := "akka-sample-fsm-scala"

val akkaVersion = "2.6.14"

scalaVersion := "2.13.5"
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "ch.qos.logback" % "logback-classic" % "1.2.3"
)

licenses := Seq(
  ("CC0", url("http://creativecommons.org/publicdomain/zero/1.0"))
)
