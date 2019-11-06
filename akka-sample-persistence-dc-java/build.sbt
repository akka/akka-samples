organization := "com.typesafe.akka.samples"
name := "akka-sample-persistence-dc-java"

enablePlugins(ProtobufPlugin)

scalaVersion := "2.13.1"

val AkkaVersion = "2.6.0"
val AkkaAddOnsVersion = "1.1.12"
val AkkaPersistenceCassandraVersion = "0.100"
val AkkaHttpVersion = "10.1.10"
val AkkaClusterManagementVersion = "1.0.3"

credentials += Credentials(Path.userHome / ".lightbend" / "commercial.credentials")
resolvers += "com-mvn" at "https://repo.lightbend.com/commercial-releases/"
resolvers += Resolver.url("com-ivy",
  url("https://repo.lightbend.com/commercial-releases/"))(Resolver.ivyStylePatterns)

libraryDependencies ++= Seq(
  "com.lightbend.akka" %% "akka-persistence-multi-dc" % AkkaAddOnsVersion,
  "com.lightbend.akka" %% "akka-persistence-multi-dc-testkit" % AkkaAddOnsVersion,
  "com.lightbend.akka" %% "akka-split-brain-resolver" % AkkaAddOnsVersion,
  "com.lightbend.akka" %% "akka-diagnostics" % AkkaAddOnsVersion,
  "com.typesafe.akka" %% "akka-cluster-sharding" % AkkaVersion,
  "com.typesafe.akka" %% "akka-persistence-query" % AkkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
  "com.lightbend.akka.management" %% "akka-management" % AkkaClusterManagementVersion,
  "com.lightbend.akka.management" %% "akka-management-cluster-http" % AkkaClusterManagementVersion,
  "com.typesafe.akka" %% "akka-persistence-cassandra-launcher" % AkkaPersistenceCassandraVersion % "test",
  "org.scalatest" %% "scalatest" % "3.0.8" % "test"
)

// transitive dependency of akka 2.5x that is brought in by addons but evicted
dependencyOverrides += "com.typesafe.akka" %% "akka-protobuf" % AkkaVersion
// The default is src/main/protobuf, but the maven plugin expects them here:
sourceDirectory in ProtobufConfig := (sourceDirectory in Compile).value / "proto"

licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0")))
