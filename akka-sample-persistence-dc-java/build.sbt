organization := "com.typesafe.akka.samples"
name := "akka-sample-persistence-dc-java"

enablePlugins(ProtobufPlugin)

scalaVersion := "2.12.6"

val AkkaVersion = "2.5.17"
val AkkaAddOnsVersion = "1.1.0"
val AkkaPersistenceCassandraVersion = "0.89"
val AkkaHttpVersion = "10.1.4"
val AkkaClusterManagementVersion = "0.17.0"

credentials += Credentials(Path.userHome / ".lightbend" / "commercial.credentials")
resolvers += "com-mvn" at "https://repo.lightbend.com/commercial-releases/"
resolvers += Resolver.url("com-ivy",
  url("https://repo.lightbend.com/commercial-releases/"))(Resolver.ivyStylePatterns)

libraryDependencies ++= Seq(
  "com.lightbend.akka" %% "akka-persistence-multi-dc" % AkkaAddOnsVersion,
  "com.lightbend.akka" %% "akka-persistence-multi-dc-testkit" % AkkaAddOnsVersion,
  "com.lightbend.akka" %% "akka-split-brain-resolver" % AkkaAddOnsVersion,
  "com.lightbend.akka" %% "akka-diagnostics" % AkkaAddOnsVersion,
  "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
  "com.lightbend.akka.management" %% "akka-management" % AkkaClusterManagementVersion,
  "com.lightbend.akka.management" %% "akka-management-cluster-http" % AkkaClusterManagementVersion,
  "com.typesafe.akka" %% "akka-persistence-cassandra-launcher" % AkkaPersistenceCassandraVersion % "test",
  "org.scalatest" %% "scalatest" % "3.0.5" % "test"
)

licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0")))
