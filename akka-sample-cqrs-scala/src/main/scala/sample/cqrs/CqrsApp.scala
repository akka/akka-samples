package sample.cqrs

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.Await
import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.actor.PoisonPill
import akka.persistence.cassandra.testkit.CassandraLauncher
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

object CqrsApp {

  def main(args: Array[String]): Unit = {
    args.headOption match {

      case None =>
        startClusterInSameJvm()

      case Some(portString) if portString.matches("""\d+""") =>
        val port = portString.toInt

        startNode(port)

      case Some("cassandra") =>
        startCassandraDatabase()
        println("Started Cassandra, press Ctrl + C to kill")
        new CountDownLatch(1).await()

    }
  }

  def startClusterInSameJvm(): Unit = {
    startCassandraDatabase()

    startNode(2551)
    startNode(2552)
  }

  def startNode(port: Int): Unit = {
    val system = ActorSystem("ClusterSystem", config(port))

    // TODO start ClusterSharding

    if (port == 2551) {
      createTables(system)

      testIt(system)
    }

  }

  def config(port: Int): Config =
    ConfigFactory.parseString(s"""
      akka.remote.artery.canonical.port = $port
      akka.remote.netty.tcp.port = $port
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

  def createTables(system: ActorSystem): Unit = {
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

  // FIXME very temporary test
  def testIt(system: ActorSystem): Unit = {
    val entity1 = system.actorOf(TestEntity.props(), "entity1")
    entity1 ! "a1"
    entity1 ! "a2"
    entity1 ! "a3"

    val entity2 = system.actorOf(TestEntity.props(), "entity2")
    entity2 ! "b1"
    entity2 ! "b2"

    val processor1 = system.actorOf(EventProcessor.props("tag1"), "processorTag1")

    entity2 ! "b3"
    Thread.sleep(10000)

    entity1 ! "a4"
    entity1 ! "a5"
    entity2 ! "b4"
    entity2 ! "b5"

    Thread.sleep(1000)
    processor1 ! PoisonPill

    entity2 ! "b6"
    entity2 ! "b7"
    entity1 ! "a6"
    entity1 ! "a7"
    Thread.sleep(1000)

    // start it again, should continue from stored offset
    system.actorOf(EventProcessor.props("tag1"), "processorTag1")

    val counter = new AtomicInteger(8)
    import system.dispatcher
    system.scheduler.schedule(3.seconds, 1.second) {
      entity1 ! s"a${counter.getAndIncrement()}"
      entity2 ! s"b${counter.getAndIncrement()}"
    }

  }

}
