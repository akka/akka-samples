import com.typesafe.sbt.SbtMultiJvm.multiJvmSettings
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

val akkaVersion = "2.6.0-RC2"

val `akka-sample-cluster-java` = project
  .in(file("."))
  .settings(multiJvmSettings: _*)
  .settings(
    organization := "com.typesafe.akka.samples",
    scalaVersion := "2.12.8",
    scalacOptions in Compile ++= Seq("-deprecation", "-feature", "-unchecked", "-Xlog-reflective-calls", "-Xlint"),
    javacOptions in Compile ++= Seq("-Xlint:unchecked", "-Xlint:deprecation"),
    javacOptions in doc in Compile := Seq("-Xdoclint:none"),
    javaOptions in run ++= Seq("-Xms128m", "-Xmx1024m", "-Djava.library.path=./target/native"),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed"           % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-typed"         % akkaVersion,
      "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion,
      "com.typesafe.akka" %% "akka-multi-node-testkit"    % akkaVersion,
      "ch.qos.logback"    %  "logback-classic"             % "1.2.3",
      "org.scalatest"     %% "scalatest"                  % "3.0.8"     % Test,
      "com.typesafe.akka" %% "akka-actor-testkit-typed"   % akkaVersion % Test),
    fork in run := true,
    mainClass in (Compile, run) := Some("sample.cluster.simple.SimpleClusterApp"),
    // disable parallel tests
    parallelExecution in Test := false,
    licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0")))
  )
  .configs (MultiJvm)
