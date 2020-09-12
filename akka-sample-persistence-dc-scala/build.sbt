organization := "com.lightbend.akka.samples"
name := "akka-sample-replicated-event-sourcing-scala"

scalaVersion := "2.13.3"

val AkkaVersion = "2.6.9"
val AkkaAddOnsVersion = "1.1.12"
val AkkaPersistenceCassandraVersion = "1.0.3"
val AkkaHttpVersion = "10.2.0"
val AkkaClusterManagementVersion = "1.0.8"

credentials += Credentials(Path.userHome / ".lightbend" / "commercial.credentials")
resolvers += "com-mvn" at "https://repo.lightbend.com/commercial-releases/"
resolvers += Resolver.url("com-ivy",
  url("https://repo.lightbend.com/commercial-releases/"))(Resolver.ivyStylePatterns)

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-cluster-sharding-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-persistence-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,
  "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
  "com.lightbend.akka.management" %% "akka-management" % AkkaClusterManagementVersion,
  "com.lightbend.akka.management" %% "akka-management-cluster-http" % AkkaClusterManagementVersion,
  "com.typesafe.akka" %% "akka-persistence-cassandra" % AkkaPersistenceCassandraVersion,

  "ch.qos.logback" % "logback-classic" % "1.2.3",

  "com.typesafe.akka" %% "akka-persistence-cassandra-launcher" % AkkaPersistenceCassandraVersion,
  "org.scalatest" %% "scalatest" % "3.0.8" % Test
)

// transitive dependency of akka 2.5x that is brought in by addons but evicted
dependencyOverrides += "com.typesafe.akka" %% "akka-protobuf" % AkkaVersion
dependencyOverrides += "com.typesafe.akka" %% "akka-cluster-tools" % AkkaVersion
dependencyOverrides += "com.typesafe.akka" %% "akka-coordination" % AkkaVersion

licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0")))
