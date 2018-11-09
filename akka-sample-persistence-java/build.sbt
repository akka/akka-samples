organization := "com.typesafe.akka.samples"
name := "akka-sample-persistence-java"

scalaVersion := "2.12.6"
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-persistence" % "2.5.18",
  "org.iq80.leveldb" % "leveldb" % "0.7",
  "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8"
)

licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0")))
