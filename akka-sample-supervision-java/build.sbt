organization := "com.typesafe.akka.samples"
name := "akka-sample-supervision-java"

scalaVersion := "2.12.1"
libraryDependencies ++= Seq(
  "com.typesafe.akka" %%      "akka-actor" % "2.4.14",
  "com.typesafe.akka" %%    "akka-testkit" % "2.4.14" % Test,
              "junit"  %           "junit" % "4.12"   % Test,
       "com.novocode"  % "junit-interface" % "0.11"   % Test)

licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0")))
