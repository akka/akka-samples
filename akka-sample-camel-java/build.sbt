organization := "com.typesafe.akka.samples"
name := "akka-sample-camel-java"

scalaVersion := "2.12.2"
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-camel" % "2.5.2",
  "org.apache.camel" % "camel-jetty" % "2.10.3",
  "org.apache.camel" % "camel-quartz" % "2.10.3",
  "org.slf4j" % "slf4j-api" % "1.7.2",
  "ch.qos.logback" % "logback-classic" % "1.0.7"
)

licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0")))
