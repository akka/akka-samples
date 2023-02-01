import com.typesafe.sbt.SbtMultiJvm.multiJvmSettings
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

val AkkaVersion = "2.7.0"
val AkkaDiagnosticsVersion = "2.0.0-M3"
val LogbackClassicVersion = "1.2.11" 
val ScalaTestVersion = "3.0.8"

lazy val `akka-sample-cluster-java` = project
  .in(file("."))
  .settings(multiJvmSettings: _*)
  .settings(
    organization := "com.lightbend.akka.samples",
    scalaVersion := "2.13.10",
    Compile / scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked", "-Xlog-reflective-calls", "-Xlint"),
    Compile / javacOptions ++= Seq("-parameters", "-Xlint:unchecked", "-Xlint:deprecation"),
    run / javaOptions ++= Seq("-Xms128m", "-Xmx1024m", "-Djava.library.path=./target/native"),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed"           % AkkaVersion,
      "com.typesafe.akka" %% "akka-cluster-typed"         % AkkaVersion,
      "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,
      "ch.qos.logback"    %  "logback-classic"            % LogbackClassicVersion,
      "com.lightbend.akka" %% "akka-diagnostics"          % AkkaDiagnostics,
      "com.typesafe.akka" %% "akka-multi-node-testkit"    % AkkaVersion % Test,
      "org.scalatest"     %% "scalatest"                  % ScalaTestVersion % Test,
      "com.typesafe.akka" %% "akka-actor-testkit-typed"   % AkkaVersion % Test),
    run / fork := false,
    Global / cancelable := false,
    // disable parallel tests
    Test / parallelExecution := false,
    licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0")))
  )
  .configs (MultiJvm)
