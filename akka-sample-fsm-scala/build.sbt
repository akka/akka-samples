organization := "com.typesafe.akka.samples"
name := "akka-sample-fsm-scala"

val akkaVersion = "2.5.17"

scalaVersion := "2.12.6"
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion
)

licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0")))
