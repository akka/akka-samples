import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport._
import com.typesafe.sbt.SbtMultiJvm.multiJvmSettings
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

val akkaVersion = "2.5.22"
enablePlugins(JavaAppPackaging, DockerPlugin)

lazy val `akka-sample-cluster-scala` = project
  .in(file("."))
  .settings(multiJvmSettings: _*)
  .settings(
    version := "0.1.0-SNAPSHOT",
    organization := "com.typesafe.akka.samples",
    scalaVersion := "2.12.8",
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
      "org.scalatest" %% "scalatest" % "3.0.7" % Test,
      "io.kamon" % "sigar-loader" % "1.6.6-rev002"),
      // Based on best practices found in OpenShift Creating images guidelines
      // https://docs.openshift.com/container-platform/3.10/creating_images/guidelines.html
      dockerRepository := Some("lightbend-docker-registry.bintray.io/simple-cluster-app"),
      dockerCommands := Seq(
        Cmd("FROM",           "centos:7"),
        Cmd("RUN",            "yum -y install java-1.8.0-openjdk-headless && yum clean all -y"),
        Cmd("RUN",            "useradd -r -m -u 1001 -g 0 simpleclusterapp"),
        Cmd("ADD",            "opt /opt"),
        Cmd("RUN",            "chgrp -R 0 /opt && chmod -R g=u /opt"),
        Cmd("WORKDIR",        "/opt/docker"),
        Cmd("USER",           "1001"),
        ExecCmd("CMD",        "/opt/docker/bin/simple-cluster-app", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseCGroupMemoryLimitForHeap"),
      ),
    fork in run := true,
    mainClass in (Compile, run) := Some("sample.cluster.simple.SimpleClusterApp"),
    // disable parallel tests
    parallelExecution in Test := false,
    licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0")))
  )
  .configs (MultiJvm)
