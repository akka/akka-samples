package sample.persistence.res

import java.io.File
import java.util.concurrent.CountDownLatch

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.ReplicatedShardingExtension
import akka.cluster.typed.Cluster
import akka.http.scaladsl.Http
import akka.management.scaladsl.AkkaManagement
import akka.persistence.cassandra.testkit.CassandraLauncher
import akka.persistence.typed.ReplicaId
import com.typesafe.config.{Config, ConfigFactory}
import sample.persistence.res.counter.{ThumbsUpCounter, ThumbsUpHttp}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object MainApp {

  val AllReplicas = Set(ReplicaId("eu-west"), ReplicaId("eu-central"))

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
    implicit val system: ActorSystem[Nothing] = ActorSystem[Nothing](Behaviors.empty[Nothing], "ClusterSystem", config(port, dc))
    implicit val ec: ExecutionContext = system.executionContext
    val thumbsUpReplicatedSharding = ReplicatedShardingExtension(system).init(ThumbsUpCounter.Provider)

    if (port != 0) {
      val httpHost = "0.0.0.0"
      val httpPort = 20000+port
      Http().newServerAt(httpHost, httpPort)
        .bind(ThumbsUpHttp.route(ReplicaId(dc), thumbsUpReplicatedSharding))
        .onComplete {
        case Success(_) => system.log.info("HTTP Server bound to http://{}:{}", httpHost, httpPort)
        case Failure(ex) => system.log.error(s"Failed to bind HTTP Server to http://$httpHost:$httpPort", ex)
      }
      AkkaManagement(system).start()
    }

  }

  def config(port: Int, dc: String): Config =
    ConfigFactory.parseString(
      s"""
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
