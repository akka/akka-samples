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
        val httpPort = ("80" + portString.takeRight(2)).toInt
        startNode(port, httpPort)

      case Some("cassandra") =>
        startCassandraDatabase()
        println("Started Cassandra, press Ctrl + C to kill")
        new CountDownLatch(1).await()

      case None =>
        throw new IllegalArgumentException("port number, or cassandra required argument")
    }
  }

  def startNode(port: Int, httpPort: Int): Unit = {
    val system = ActorSystem[Nothing](Guardian(), "Shopping", config(port, httpPort))

    if (Cluster(system).selfMember.hasRole("read-model"))
      createTables(system)
  }

  def config(port: Int, httpPort: Int): Config =
    ConfigFactory.parseString(s"""
      akka.remote.artery.canonical.port = $port
      shopping.http.port = $httpPort
       """).withFallback(ConfigFactory.load())

  /**
   * To make the sample easier to run we kickstart a Cassandra instance to
   * act as the journal. Cassandra is a great choice of backend for Akka Persistence but
   * in a real application a pre-existing Cassandra cluster should be used.
   */
  def startCassandraDatabase(): Unit = {
    val databaseDirectory = new File("target/cassandra-db")
    CassandraLauncher.start(databaseDirectory, CassandraLauncher.DefaultTestConfigResource, clean = false, port = 9042)
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
    Await.ready(session.executeDDL(keyspaceStmt), 30.seconds)
    Await.ready(session.executeDDL(offsetTableStmt), 30.seconds)
  }

}

object Guardian {
  def apply(): Behavior[Nothing] = {
    Behaviors.setup[Nothing] { context =>
      val system = context.system
      val settings = EventProcessorSettings(system)
      val httpPort = context.system.settings.config.getInt("shopping.http.port")

      ShoppingCart.init(system, settings)

      if (Cluster(system).selfMember.hasRole("read-model")) {
        // FIXME, the tables may not be created yet, send a start message they're done
        EventProcessor.init(
          system,
          settings,
          tag => new ShoppingCartEventProcessorStream(system, system.executionContext, settings.id, tag))
      }

      val routes = new ShoppingCartRoutes()(context.system)
      new ShoppingCartServer(routes.shopping, httpPort, context.system).start()

      Behaviors.empty
    }
  }
}
