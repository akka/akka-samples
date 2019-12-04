import com.typesafe.sbt.SbtMultiJvm.multiJvmSettings
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

val akkaVersion = "2.6.0"

lazy val `akka-sample-cluster-client-grpc-scala` = project
  .in(file("."))
  .enablePlugins(JavaAgent)
  .enablePlugins(AkkaGrpcPlugin)
  .settings(multiJvmSettings: _*)
  .settings(
    organization := "com.typesafe.akka",
    scalaVersion := "2.12.8",
    scalacOptions in Compile ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Xlog-reflective-calls",
      "-Xlint"
    ),
    javacOptions in Compile ++= Seq("-Xlint:unchecked", "-Xlint:deprecation"),
    // javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.9" % "runtime",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
      "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion,
      "com.typesafe.akka" %% "akka-discovery" % akkaVersion,
      "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion % Test,
      "org.scalatest" %% "scalatest" % "3.0.1" % Test
    )
  )
  .configs(MultiJvm)
