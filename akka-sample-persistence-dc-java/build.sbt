organization := "com.typesafe.akka.samples"
name := "akka-sample-persistence-dc-java"

scalaVersion := "2.12.4"

val akkaVersion = "2.5.8"
val akkaAddOnsVersion = "1.1.0-RC1"
val akkaPersistenceCassandraVersion = "0.80-RC3"

credentials += Credentials(Path.userHome / ".lightbend" / "commercial.credentials")
resolvers += "com-mvn" at "https://repo.lightbend.com/commercial-releases/"
resolvers += Resolver.url("com-ivy",
  url("https://repo.lightbend.com/commercial-releases/"))(Resolver.ivyStylePatterns)

libraryDependencies ++= Seq(
  "com.lightbend.akka" %% "akka-persistence-multi-dc" % akkaAddOnsVersion,
  "com.lightbend.akka" %% "akka-persistence-multi-dc-testkit" % akkaAddOnsVersion,
  "com.typesafe.akka" %% "akka-persistence-cassandra-launcher" % akkaPersistenceCassandraVersion % "test",
  "org.scalatest" %% "scalatest" % "3.0.1" % "test"
)

licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0")))
