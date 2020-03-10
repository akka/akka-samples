val AkkaVersion = "2.6.3+88-03ed3def"
val AkkaPersistenceCassandraVersion = "0.102"
val AkkaHttpVersion = "10.1.10"

lazy val `akka-sample-cqrs-scala` = project
  .in(file("."))
  .settings(
    organization := "com.lightbend.akka.samples",
    version := "1.0",
    scalaVersion := "2.13.1",
    scalacOptions in Compile ++= Seq("-deprecation", "-feature", "-unchecked", "-Xlog-reflective-calls", "-Xlint"),
    javacOptions in Compile ++= Seq("-Xlint:unchecked", "-Xlint:deprecation"),
    libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-cluster-sharding-typed" % AkkaVersion,
        "com.typesafe.akka" %% "akka-persistence-typed" % AkkaVersion,
        "com.typesafe.akka" %% "akka-persistence-query" % AkkaVersion,
        "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,
        "com.typesafe.akka" %% "akka-persistence-cassandra" % AkkaPersistenceCassandraVersion,
        "com.typesafe.akka" %% "akka-persistence-cassandra-launcher" % AkkaPersistenceCassandraVersion,
        "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
        "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
        "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
        "ch.qos.logback" % "logback-classic" % "1.2.3",
        "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
        "org.scalatest" %% "scalatest" % "3.1.0" % Test,
        "commons-io" % "commons-io" % "2.4" % Test),
    fork in run := false,
    Global / cancelable := false, // ctrl-c
    mainClass in (Compile, run) := Some("sample.cqrs.Main"),
    // disable parallel tests
    parallelExecution in Test := false,
    // show full stack traces and test case durations
    testOptions in Test += Tests.Argument("-oDF"),
    logBuffered in Test := false,
    licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0"))))
