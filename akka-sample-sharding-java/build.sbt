val akkaVersion = "2.6.0-M3"

val `akka-sample-sharding-java` = project
  .in(file("."))
  .settings(
    organization := "com.typesafe.akka.samples",
    scalaVersion := "2.12.8",
    scalacOptions in Compile ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Xlog-reflective-calls",
      "-Xlint"
    ),
    javacOptions in Compile ++= Seq("-Xlint:unchecked", "-Xlint:deprecation"),
    javacOptions in doc in Compile := Seq("-Xdoclint:none"),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion,
      "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion,
      "org.scalatest" %% "scalatest" % "3.0.7" % Test
    ),
    mainClass in (Compile, run) := Some("sample.sharding.ShardingApp"),
    // disable parallel tests
    parallelExecution in Test := false,
    licenses := Seq(
      ("CC0", url("http://creativecommons.org/publicdomain/zero/1.0"))
    )
  )
