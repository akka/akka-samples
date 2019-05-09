organization := "com.typesafe.akka.samples"

val akkaVersion = "2.6.0-M1"

libraryDependencies ++= Seq(
  "com.typesafe.akka"          %% "akka-persistence-typed"   % akkaVersion,
  "org.iq80.leveldb"            % "leveldb"                  % "0.7",
  "org.fusesource.leveldbjni"   % "leveldbjni-all"           % "1.8",
  "com.typesafe.akka"          %% "akka-actor-testkit-typed" % akkaVersion % Test,
  "org.scalatest"              %% "scalatest"                % "3.0.7"     % Test
)

// To enable https://developer.lightbend.com/docs/telemetry/current
//cinnamon in run := true
//libraryDependencies += Cinnamon.library.cinnamonAkka
//enablePlugins(Cinnamon)

licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0")))
