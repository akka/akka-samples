package sample.cqrs

import java.io.File
import java.util.concurrent.CountDownLatch

import scala.concurrent.Await
import scala.concurrent.duration._

import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.typed.Cluster
import akka.persistence.cassandra.testkit.CassandraLauncher
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

object Main {

  def main(args: Array[String]): Unit = {
    args.headOption match {

      case Some(portString) if portString.matches("""\d+""") =>
        val port = portString.toInt
        startNode(port)

      case Some("cassandra") =>
        startCassandraDatabase()
        println("Started Cassandra, press Ctrl + C to kill")
        new CountDownLatch(1).await()

      case None =>
        throw new IllegalArgumentException("port number, or cassandra required argument")
    }
  }

  def startNode(port: Int): Unit = {
    val system = ActorSystem[Nothing](Guardian(), "ClusterSystem", config(port))

    if (Cluster(system).selfMember.hasRole("read-model"))
      createTables(system)
  }

  def config(port: Int): Config =
    ConfigFactory.parseString(s"""
      akka.remote.artery.canonical.port = $port
       """).withFallback(ConfigFactory.load("application.conf"))

  /**
   * To make the sample easier to run we kickstart a Cassandra instance to
   * act as the journal. Cassandra is a great choice of backend for Akka Persistence but
   * in a real application a pre-existing Cassandra cluster should be used.
   */
  def startCassandraDatabase(): Unit = {
    val databaseDirectory = new File("target/cassandra-db")
    CassandraLauncher.start(databaseDirectory, CassandraLauncher.DefaultTestConfigResource, clean = true, port = 9042)
  }

  def createTables(system: ActorSystem[_]): Unit = {
    val session = CassandraSessionExtension(system).session

    // TODO use real replication strategy in real application
    val keyspaceStmt = """
      CREATE KEYSPACE IF NOT EXISTS akka_cqrs_sample
      WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 }
      """

    val offsetTableStmt =
      """
      CREATE TABLE IF NOT EXISTS akka_cqrs_sample.offsetStore (
        eventProcessorId text,
        tag text,
        timeUuidOffset timeuuid,
        PRIMARY KEY (eventProcessorId, tag)
      )
      """

    // ok to block here, main thread
    Await.ready(session.executeCreateTable(keyspaceStmt), 30.seconds)
    Await.ready(session.executeCreateTable(offsetTableStmt), 30.seconds)
  }

}

object Guardian {
  def apply(): Behavior[Nothing] = {
    Behaviors.setup[Nothing] { context =>
      val system = context.system
      val selfRoles = Cluster(system).selfMember.roles

      if (selfRoles.contains("write-model")) {
        ShoppingCart.init(system)
      }

      if (selfRoles.contains("read-model")) {
        val settings = EventProcessorSettings(system)
        EventProcessor.init(
          context.system,
          settings,
          tag => new ShoppingCartEventProcessorStream(system, system.executionContext, settings.id, tag))
      }

      Behaviors.empty
    }
  }
}
