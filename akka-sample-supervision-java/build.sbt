organization := "com.typesafe.akka.samples"
name := "akka-sample-supervision-java"

val akkaVersion = "2.5.18"

scalaVersion := "2.12.6"
libraryDependencies ++= Seq(
  "com.typesafe.akka" %%      "akka-actor" % akkaVersion,
  "com.typesafe.akka" %%    "akka-testkit" % akkaVersion % Test,
              "junit"  %           "junit" % "4.12"      % Test,
       "com.novocode"  % "junit-interface" % "0.11"      % Test)

licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0")))
