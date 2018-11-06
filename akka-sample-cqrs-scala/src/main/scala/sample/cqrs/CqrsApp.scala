package sample.cqrs

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.persistence.cassandra.testkit.CassandraLauncher
import com.typesafe.config.{Config, ConfigFactory}

import scala.collection.breakOut
import scala.concurrent.Await
import scala.concurrent.duration._

object CqrsApp {

  private val Opt = """(\S+)=(\S+)""".r

  private def argsToOpts(args: Seq[String]): Map[String, String] =
    args.collect { case Opt(key, value) => key -> value }(breakOut)

  private def applySystemProperties(options: Map[String, String]): Unit =
    for ((key, value) <- options if key startsWith "-D")
      System.setProperty(key.substring(2), value)

  def main(args: Array[String]): Unit = {

    val opts = argsToOpts(args.toList)
    applySystemProperties(opts)

    args.headOption match {

      case Some(portString) if portString.matches("""\d+""") =>
        val port = portString.toInt

        startNode(port)

      case Some("cassandra") =>
        startCassandraDatabase()
        println("Started Cassandra, press Ctrl + C to kill")
        new CountDownLatch(1).await()

    }
  }

  def startNode(port: Int): Unit = {

    val system = ActorSystem("ClusterSystem", config(port))

    val selfRoles = Cluster(system).selfRoles

    if (selfRoles.contains("write-model")) {

      createTables(system)

      testIt(system)
    }

    if (selfRoles.contains("read-model"))
      EventProcessorWrapper(system).start()
  }

  def config(port: Int): Config =
    ConfigFactory.parseString(
      s"""akka.remote.artery.canonical.port = $port
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
      clean = true,
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

    ShardedSwitchEntity(system).start()

    val shardedSwitch = ShardedSwitchEntity(system)

    shardedSwitch.tell("switch", SwitchEntity.CreateSwitch(4))
    shardedSwitch.tell("switch1", SwitchEntity.SetPortStatus(2, portEnabled = true))
    shardedSwitch.tell("switch1", SwitchEntity.SendPortStatus)

    shardedSwitch.tell("switch2", SwitchEntity.CreateSwitch(6))
    shardedSwitch.tell("switch2", SwitchEntity.SetPortStatus(0, portEnabled = true))
    shardedSwitch.tell("switch2", SwitchEntity.SetPortStatus(2, portEnabled = true))
    shardedSwitch.tell("switch2", SwitchEntity.SetPortStatus(5, portEnabled = true))
    shardedSwitch.tell("switch2", SwitchEntity.SendPortStatus)


    val counter = new AtomicInteger(8)
    import system.dispatcher
    system.scheduler.schedule(3.seconds, 1.second) {
      import scala.util.Random
      val switch = s"switch${counter.getAndIncrement()}"
      shardedSwitch.tell(switch, SwitchEntity.CreateSwitch(6))
      shardedSwitch.tell(switch, SwitchEntity.SetPortStatus(Random.nextInt(6), portEnabled = true))
      shardedSwitch.tell(switch, SwitchEntity.SetPortStatus(Random.nextInt(6), portEnabled = true))
      shardedSwitch.tell(switch, SwitchEntity.SendPortStatus)
    }
  }
}
