import com.typesafe.sbt.SbtMultiJvm.multiJvmSettings
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

val akkaVersion = "2.6.0"

lazy val `akka-sample-sharding-scala` = project
  .in(file("."))
  .settings(multiJvmSettings: _*)
  .settings(
    organization := "com.typesafe.akka.samples",
    scalaVersion := "2.12.8",
    scalacOptions in Compile ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Xlog-reflective-calls",
      "-Xlint"
    ),
    javacOptions in Compile ++= Seq("-Xlint:unchecked", "-Xlint:deprecation"),
    javaOptions in run ++= Seq("-Xms128m", "-Xmx1024m"),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-sharding-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion,
      "org.scalatest" %% "scalatest" % "3.0.8" % Test
    ),
    mainClass in (Compile, run) := Some("sample.sharding.ShardingApp"),
    // disable parallel tests
    parallelExecution in Test := false,
    licenses := Seq(
      ("CC0", url("http://creativecommons.org/publicdomain/zero/1.0"))
    )
  )
  .configs(MultiJvm)
