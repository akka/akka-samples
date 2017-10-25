organization := "com.typesafe.akka.samples"
name := "akka-sample-persistence-multi-dc-scala"

scalaVersion := "2.12.1"

credentials += Credentials(Path.userHome / ".lightbend" / "commercial.credentials")
resolvers += "com-mvn" at "https://repo.lightbend.com/commercial-releases/"
resolvers += Resolver.url("com-ivy",
  url("https://repo.lightbend.com/commercial-releases/"))(Resolver.ivyStylePatterns)

val persistenceMultiDcVersion = "1.1-M4+3-667a6ef6"

libraryDependencies ++= Seq(
  "com.lightbend.akka" %% "akka-persistence-multi-dc" % persistenceMultiDcVersion,
  "com.lightbend.akka" %% "akka-persistence-multi-dc-testkit" % persistenceMultiDcVersion % "test",
  "org.scalatest" %% "scalatest" % "3.0.1" % "test"
)

licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0")))
