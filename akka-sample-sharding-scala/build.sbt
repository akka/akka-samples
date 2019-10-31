
val AkkaVersion = "2.6.0-RC2"

lazy val buildSettings = Seq(
  organization := "com.lightbend.akka.samples",
  scalaVersion := "2.12.10"
)

lazy val commonScalacOptions = Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlint",
  "-encoding", "UTF-8"
)

lazy val commonJavacOptions = Seq(
  "-Xlint:unchecked",
  "-Xlint:deprecation"
)

lazy val `akka-sample-sharding-scala` = project
  .in(file("."))
  .settings(
    Compile / scalacOptions ++= commonScalacOptions,
    Compile / javacOptions ++= commonJavacOptions,
    run / javaOptions ++= Seq("-Xms128m", "-Xmx1024m"),

    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-cluster-sharding-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,
      "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
      "com.typesafe.akka" %% "akka-http" % "10.1.10",
      "com.typesafe.akka" %% "akka-http-spray-json" % "10.1.10",
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
      "org.scalatest" %% "scalatest" % "3.0.7" % Test
    ),
    mainClass in (Compile, run) := Some("sample.sharding.Main"),
    licenses := Seq(
      ("CC0", url("http://creativecommons.org/publicdomain/zero/1.0"))
    ),

    // Startup aliases for the first two seed nodes and a third, more can be started.
    addCommandAlias("sharding1", "runMain sample.sharding.KillrWeather 2551"),
    addCommandAlias("sharding2", "runMain sample.sharding.KillrWeather 2552"),
    addCommandAlias("sharding3", "runMain sample.sharding.KillrWeather 0"),
  )
