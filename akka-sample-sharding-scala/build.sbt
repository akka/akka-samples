import com.typesafe.sbt.SbtMultiJvm.multiJvmSettings
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

val AkkaVersion = "2.6.0-RC1"

lazy val buildSettings = Seq(
  organization := "com.lightbend.akka.samples",
  scalaVersion := "2.12.10"
)

lazy val disciplineScalacOptions = Seq(
  // not in 2.13
  "-Ywarn-value-discard",
  "-Ywarn-numeric-widen",
  "-Yno-adapted-args",
  // end
  "-Xlog-reflective-calls",
  "-Xlint",
  "-deprecation",
  "-Ywarn-dead-code",
  "-Ywarn-inaccessible",
  "-Ywarn-infer-any",
  "-Ywarn-nullary-override",
  "-Ywarn-nullary-unit",
  "-Ywarn-unused:_",
  "-Ypartial-unification",
  "-Ywarn-extra-implicit"
)

lazy val commonScalacOptions =
  disciplineScalacOptions ++ Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-encoding", "UTF-8"
)

lazy val commonJavacOptions = Seq(
  "-Xlint:unchecked",
  "-Xlint:deprecation"
)

lazy val `akka-sample-sharding-scala` = project
  .in(file("."))
  .settings(multiJvmSettings: _*)
  .settings(
    Compile / scalacOptions ++= commonScalacOptions,
    Compile / javacOptions ++= commonJavacOptions,
    run / javaOptions ++= Seq("-Xms128m", "-Xmx1024m"),

    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-cluster-sharding-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,
      "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
      "ch.qos.logback" % "logback-classic" % "1.2.3",
    ),
    mainClass in (Compile, run) := Some("sample.sharding.ShardingApp"),
    licenses := Seq(
      ("CC0", url("http://creativecommons.org/publicdomain/zero/1.0"))
    ),

    addCommandAlias("sharding1", "runMain sample.sharding.ShardingApp 2551"),
    addCommandAlias("sharding2", "runMain sample.sharding.ShardingApp 2552"),
    addCommandAlias("sharding3", "runMain sample.sharding.ShardingApp 0"),
  )
  .configs(MultiJvm)
