import com.typesafe.sbt.SbtMultiJvm.multiJvmSettings
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm
import com.typesafe.sbt.packager.docker.{ ExecCmd, Cmd }

val akkaVersion = "2.5.3"

lazy val `akka-sample-cluster-istio-scala` = project
  .in(file("."))
  .aggregate(frontend, backend)

lazy val commonSettings = Seq(
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
    "org.scalatest" %% "scalatest" % "3.0.1" % Test,
    "io.kamon" % "sigar-loader" % "1.6.6-rev002"),
  fork in run := true,
  mainClass in (Compile, run) := Some("sample.cluster.simple.SimpleClusterApp"),
  // disable parallel tests
  parallelExecution in Test := false,
  licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0"))),
  //dockerBaseImage := "openjdk:8-jre-alpine",
  dockerEntrypoint ++= Seq(
    """-Dakka.remote.netty.tcp.hostname="$AKKA_REMOTING_BIND_HOST"""",
    """-Dakka.remote.netty.tcp.port="$AKKA_REMOTING_BIND_PORT"""",
    """-Dakka.cluster.seed-nodes.0="akka.tcp://${AKKA_ACTOR_SYSTEM_NAME}@${AKKA_SEED_NODE_HOST}:${AKKA_SEED_NODE_PORT}"""",
    """-Dakka.io.dns.resolver=async-dns""",
    """-Dakka.io.dns.async-dns.resolve-srv=true""",
    """-Dakka.io.dns.async-dns.resolv-conf=on""",
    """-Dsample.cluster.actor-system-name=$AKKA_ACTOR_SYSTEM_NAME"""
  ),
  dockerCommands :=
    dockerCommands.value.flatMap {
      case ExecCmd("ENTRYPOINT", args @ _*) => Seq(Cmd("ENTRYPOINT", args.mkString(" ")))
      //case from @ Cmd("FROM", _) => Seq(from, Cmd("RUN", "apk --no-cache add --update bash coreutils"))
      case v => Seq(v)
    },
  dockerRepository := Some("com-typesafe-akka-samples"),
  dockerUpdateLatest := true
)

lazy val frontend = project
  .in(file("frontend"))
  .settings(multiJvmSettings: _*)
  .settings(commonSettings)
  .settings((packageName in Docker) := "akka-sample-cluster-istio-scala-frontend")
  .configs (MultiJvm)
  .enablePlugins(JavaAppPackaging)

lazy val backend = project
  .in(file("backend"))
  .settings(multiJvmSettings: _*)
  .settings(commonSettings)
  .settings((packageName in Docker) := "akka-sample-cluster-istio-scala-backend")
  .configs (MultiJvm)
  .enablePlugins(JavaAppPackaging)