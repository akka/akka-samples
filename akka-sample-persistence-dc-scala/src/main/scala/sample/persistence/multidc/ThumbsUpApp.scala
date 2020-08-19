package sample.persistence.multidc

import java.io.File
import java.util.concurrent.CountDownLatch

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.{ReplicatedShardingExtension, ShardingEnvelope}
import akka.cluster.typed.Cluster
import akka.management.scaladsl.AkkaManagement
import akka.persistence.cassandra.testkit.CassandraLauncher
import akka.persistence.typed.ReplicaId
import com.typesafe.config.{Config, ConfigFactory}

object ThumbsUpApp {

  def main(args: Array[String]): Unit = {
    args.headOption match {

      case None =>
        startClusterInSameJvm()

      case Some(portString) if portString.matches("""\d+""") =>
        val port = portString.toInt
        val dc = args.tail.headOption.getOrElse("eu-west")
        startNode(port, dc)

      case Some("cassandra") =>
        startCassandraDatabase()
        println("Started Cassandra, press Ctrl + C to kill")
        new CountDownLatch(1).await()

    }
  }

  def startClusterInSameJvm(): Unit = {
    startCassandraDatabase()

    startNode(2551, "eu-west")
    startNode(2552, "eu-central")
  }

  def startNode(port: Int, dc: String): Unit = {
    val system: ActorSystem[Nothing] = ActorSystem[Nothing](Behaviors.empty[Nothing], "ClusterSystem", config(port, dc))


    val cluster = Cluster(system)
    val replicatedSharding = ReplicatedShardingExtension(system).init(ThumbsUpCounter.Provider)


    if (port != 0) {
      ThumbsUpHttp.startServer("0.0.0.0", 20000 + port, ReplicaId(cluster.selfMember.dataCenter), replicatedSharding)(system)
      AkkaManagement(system).start()
    }

  }

  def config(port: Int, dc: String): Config =
    ConfigFactory.parseString(s"""
      akka.remote.artery.canonical.port = $port
      akka.management.http.port = 1$port
      akka.cluster.multi-data-center.self-data-center = $dc
    """).withFallback(ConfigFactory.load("application.conf"))

  /**
   * To make the sample easier to run we kickstart a Cassandra instance to
   * act as the journal. Cassandra is a great choice of backend for Akka Persistence but
   * in a real application a pre-existing Cassandra cluster should be used.
   */
  def startCassandraDatabase(): Unit = {
    val databaseDirectory = new File("target/cassandra-db")
    CassandraLauncher.start(
      databaseDirectory,
      CassandraLauncher.DefaultTestConfigResource,
      clean = false,
      port = 9042)
  }

}
