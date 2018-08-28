organization := "com.typesafe.akka.samples"
name := "akka-sample-fsm-java"

val akkaVersion = "2.5.15"

scalaVersion := "2.12.6"
crossScalaVersions := Seq("2.12.6", "2.11.11")
libraryDependencies ++= Seq(
  "com.typesafe.akka" %%      "akka-actor" % akkaVersion,
  "com.typesafe.akka" %%    "akka-testkit" % akkaVersion % Test,
              "junit"  %           "junit" % "4.11"      % Test,
       "com.novocode"  % "junit-interface" % "0.10"      % Test)

licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0")))
