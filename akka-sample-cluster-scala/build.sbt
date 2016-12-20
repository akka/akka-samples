import com.typesafe.sbt.SbtMultiJvm.multiJvmSettings
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

val akkaVersion = "2.4.14"

lazy val `akka-sample-cluster-scala` = project
  .in(file("."))
  .settings(multiJvmSettings: _*)
  .settings(
    scalaVersion := "2.12.1",
    scalacOptions in Compile ++= Seq("-deprecation", "-feature", "-unchecked", "-Xlog-reflective-calls", "-Xlint"),
    javacOptions in Compile ++= Seq("-Xlint:unchecked", "-Xlint:deprecation"),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % akkaVersion,
      "com.typesafe.akka" %% "akka-remote" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-metrics" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
      "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion,
      "org.scalatest" %% "scalatest" % "3.0.1" % "test",
      "io.kamon" % "sigar-loader" % "1.6.6-rev002"),
    javaOptions in run ++= Seq("-Xms128m", "-Xmx1024m", "-Djava.library.path=./target/native"),
    fork in run := true,
    mainClass in (Compile, run) := Some("sample.cluster.simple.SimpleClusterApp"),
    // make sure that MultiJvm test are compiled by the default test compilation
    compile in MultiJvm := ((compile in MultiJvm) triggeredBy (compile in Test)).value,
    // disable parallel tests
    parallelExecution in Test := false,
    // make sure that MultiJvm tests are executed by the default test target,
    // and combine the results from ordinary test and multi-jvm tests
    executeTests in Test := {
      val testResults = (executeTests in Test).value
      val multiNodeResults = (executeTests in MultiJvm).value
      val overall =
        if (testResults.overall.id < multiNodeResults.overall.id)
          multiNodeResults.overall
        else
          testResults.overall
      Tests.Output(overall,
        testResults.events ++ multiNodeResults.events,
        testResults.summaries ++ multiNodeResults.summaries)
    },
    licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0")))
  )
  .configs (MultiJvm)
