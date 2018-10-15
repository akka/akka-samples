import com.typesafe.sbt.SbtMultiJvm.multiJvmSettings
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

val akkaVersion = "2.5.3"

lazy val dockerSettings = Seq(
  dockerBaseImage := "openjdk:8-jre",
  dockerRepository := Some("akkasamples"),
  packageName := "akka-sample-mesos-scala",
  dockerExposedPorts := Seq(2551),
  version := "latest",
  dockerEntrypoint := Seq(
      "./bin/akka-sample-mesos-scala"
  )
)

lazy val `akka-sample-mesos-scala` = project
  .in(file("."))
  .enablePlugins(JavaAppPackaging, JavaServerAppPackaging, DockerPlugin)
  .settings(multiJvmSettings: _*)
  .settings(
    organization := "com.typesafe.akka.samples",
    scalaVersion := "2.12.2",
    scalacOptions in Compile ++= Seq("-deprecation", "-feature", "-unchecked", "-Xlog-reflective-calls", "-Xlint"),
    javacOptions in Compile ++= Seq("-Xlint:unchecked", "-Xlint:deprecation"),
    javaOptions in run ++= Seq("-Xms128m", "-Xmx1024m", "-Djava.library.path=./target/native"),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % akkaVersion,
      "com.typesafe.akka" %% "akka-remote" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-metrics" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
      "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion,
      "org.scalatest" %% "scalatest" % "3.0.1" % Test),
    resourceDirectory in Compile := baseDirectory.value / "src" / "main" / "resources",
    fork in run := true,
    mainClass in (Compile, run) := Some("sample.cluster.mesos.SimpleMesosClusterApp"),
    // disable parallel tests
    parallelExecution in Test := false,
    licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0")))
  )
  .settings(dockerSettings: _*)
  .configs (MultiJvm)


