import com.typesafe.sbt.SbtMultiJvm.multiJvmSettings
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

val AkkaVersion = "2.7.0"
val AkkaDiagnosticsVersion = "2.0.0-M3"
val ScalaTestVersion = "3.1.1"

lazy val `akka-sample-cluster-client-grpc-scala` = project
  .in(file("."))
  .enablePlugins(JavaAgent)
  .enablePlugins(AkkaGrpcPlugin)
  .settings(multiJvmSettings: _*)
  .settings(
    organization := "com.typesafe.akka",
    scalaVersion := "2.13.10",
    Compile / scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Xlog-reflective-calls",
      "-Xlint"
    ),
    Compile / javacOptions ++= Seq("-Xlint:unchecked", "-Xlint:deprecation"),
    // javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.9" % "runtime",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-cluster" % AkkaVersion,
      "com.typesafe.akka" %% "akka-cluster-tools" % AkkaVersion,
      "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,
      "com.typesafe.akka" %% "akka-discovery" % AkkaVersion,
      "com.lightbend.akka" %% "akka-diagnostics" % AkkaDiagnosticsVersion,
      "com.typesafe.akka" %% "akka-multi-node-testkit" % AkkaVersion % Test,
      "org.scalatest" %% "scalatest" % ScalaTestVersion % Test
    ),
    run / fork := true
  )
  .configs(MultiJvm)
