organization := "com.typesafe.akka.samples"
name := "akka-sample-fsm-scala"

scalaVersion := "2.12.6"
crossScalaVersions := Seq("2.12.6", "2.11.11")
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.5.15"
)

licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0")))
