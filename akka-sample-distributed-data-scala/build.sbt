import com.typesafe.sbt.SbtMultiJvm.multiJvmSettings
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

val akkaVersion = "2.5.4"

val elastic4sVersion = "5.5.3"      // 5.5.3

val `akka-sample-distributed-data-scala` = project
  .in(file("."))
  .settings(multiJvmSettings: _*)
  .settings(
    organization := "com.typesafe.akka.samples",
    scalaVersion := "2.12.3",
    scalacOptions in Compile ++= Seq("-deprecation", "-feature", "-unchecked", "-Xlog-reflective-calls", "-Xlint"),
    javacOptions in Compile ++= Seq("-Xlint:unchecked", "-Xlint:deprecation"),
    javaOptions in run ++= Seq("-Xms128m", "-Xmx1024m"),
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
      libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % akkaVersion,
      "com.typesafe.akka" %% "akka-remote" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
      "com.typesafe.akka" %% "akka-distributed-data" % akkaVersion,
      "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion,
      "com.h2database"  %  "h2" % "1.4.196",
      "com.typesafe.slick" %% "slick" % "3.2.1",
      "com.chuusai" %% "shapeless" % "2.3.2",
      "org.typelevel" %% "cats" % "0.9.0",
      "org.typelevel" %% "spire" % "0.14.1",
      "org.scalaz" %% "scalaz-core" % "7.2.15",
      "org.scalaz" %% "scalaz-concurrent" % "7.2.15",
      "io.spray" %%  "spray-json" % "1.3.3",
      "io.monix" %% "monix" % "2.3.0",
      "com.github.nscala-time" %% "nscala-time" % "2.16.0",
      "com.sksamuel.elastic4s" %% "elastic4s-core" % elastic4sVersion,
      "com.sksamuel.elastic4s" %% "elastic4s-http" % elastic4sVersion,
      "com.sksamuel.elastic4s" %% "elastic4s-streams" % elastic4sVersion,
      "com.sksamuel.elastic4s" %% "elastic4s-testkit" % elastic4sVersion % Test,
      "com.sksamuel.elastic4s" %% "elastic4s-embedded" % elastic4sVersion % Test,
      "org.scalacheck" %% "scalacheck" % "1.13.4" % Test,
      "org.specs2" %% "specs2-core" % "3.9.5" % Test,
      "org.specs2" %% "specs2-scalacheck" % "3.9.5",
      "org.scalatest" %% "scalatest" % "3.0.1" % Test),
    fork in run := true,
    // disable parallel tests
    parallelExecution in Test := false,
    licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0")))
  )
  .configs (MultiJvm)
