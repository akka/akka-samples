organization := "com.typesafe.akka.samples"
name := "akka-sample-camel-scala"

scalaVersion := "2.12.6"
crossScalaVersions := Seq("2.12.6", "2.11.11")
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-camel" % "2.5.15",
  "org.apache.camel" % "camel-jetty" % "2.17.7",
  "org.apache.camel" % "camel-quartz" % "2.17.7",
  "org.slf4j" % "slf4j-api" % "1.7.23",
  "ch.qos.logback" % "logback-classic" % "1.2.3"
)

licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0")))
