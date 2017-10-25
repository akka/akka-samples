organization := "com.typesafe.akka.samples"
name := "akka-sample-persistence-multi-dc-scala"

scalaVersion := "2.12.1"

credentials += Credentials(Path.userHome / ".lightbend" / "commercial.credentials")
resolvers += "com-mvn" at "https://repo.lightbend.com/commercial-releases/"
resolvers += Resolver.url("com-ivy",
  url("https://repo.lightbend.com/commercial-releases/"))(Resolver.ivyStylePatterns)

libraryDependencies ++= Seq(
  "com.lightbend.akka" %% "akka-persistence-multi-dc" % "1.1-M4",
  // TODO replace with akka-persistence-multi-dc-testkit and move test infra there
  "com.typesafe.akka" %% "akka-testkit" % "2.5.6" % "test",
  // TODO make dependency of akka-persistence-multi-dc-testkit
  "com.typesafe.akka" %% "akka-stream-contrib" % "0.8" % "test",
  "com.typesafe.akka" %% "akka-persistence-cassandra-launcher" % "0.58" % "test",
  "org.scalatest" %% "scalatest" % "3.0.1" % "test"
)

licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0")))
