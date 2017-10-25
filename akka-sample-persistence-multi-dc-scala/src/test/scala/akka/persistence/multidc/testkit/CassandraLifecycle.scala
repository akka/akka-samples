/**
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.persistence.multidc.testkit

import java.io.File
import java.util.concurrent.TimeUnit

import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.actor.Props
import akka.persistence.PersistentActor
import akka.persistence.cassandra.testkit.CassandraLauncher
import akka.testkit.TestKitBase
import akka.testkit.TestProbe
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Suite

object CassandraLifecycle {

  val config = ConfigFactory.parseString(s"""
    akka.persistence.snapshot-store.plugin = "cassandra-snapshot-store"
    cassandra-journal-multi-dc.port = ${CassandraLauncher.randomPort}
    cassandra-snapshot-store.port = ${CassandraLauncher.randomPort}
    cassandra-journal-multi-dc.circuit-breaker.call-timeout = 30s
    akka.test.single-expect-default = 10s
    """)

  def awaitPersistenceInit(system: ActorSystem, journalPluginId: String = "", snapshotPluginId: String = ""): Unit = {
    val probe = TestProbe()(system)
    val t0 = System.nanoTime()
    var n = 0
    probe.within(45.seconds) {
      probe.awaitAssert {
        n += 1
        system.actorOf(Props(classOf[AwaitPersistenceInit], journalPluginId, snapshotPluginId), "persistenceInit" + n).tell("hello", probe.ref)
        probe.expectMsg(5.seconds, "hello")
        system.log.debug("awaitPersistenceInit took {} ms {}", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0), system.name)
      }
    }
  }

  class AwaitPersistenceInit(
    override val journalPluginId:  String,
    override val snapshotPluginId: String) extends PersistentActor {
    def persistenceId: String = "persistenceInit"

    def receiveRecover: Receive = {
      case _ =>
    }

    def receiveCommand: Receive = {
      case msg =>
        persist(msg) { _ =>
          sender() ! msg
          context.stop(self)
        }
    }
  }

  def startCassandra(systemName: String, cassandraConfigResource: String): Unit =
    startCassandra(None, None, systemName, cassandraConfigResource)

  def startCassandra(host: Option[String], port: Option[Int],
                     systemName:              String,
                     cassandraConfigResource: String = CassandraLauncher.DefaultTestConfigResource): Unit = {
    val cassandraDirectory = new File("target/" + systemName)
    CassandraLauncher.start(
      cassandraDirectory,
      configResource = cassandraConfigResource,
      clean = true,
      port = port.getOrElse(0),
      CassandraLauncher.classpathForResources("logback-test.xml"),
      host)
  }

  def stopCassandra(): Unit = {
    CassandraLauncher.stop()
  }

  def awaitPersistenceInit(system: ActorSystem): Unit = {
    CassandraLifecycle.awaitPersistenceInit(system, journalPluginId = "cassandra-journal-multi-dc")
  }

}

trait CassandraLifecycleScalatest extends BeforeAndAfterAll { this: TestKitBase with Suite =>

  import CassandraLifecycle._

  def systemName: String

  def cassandraConfigResource: String = CassandraLauncher.DefaultTestConfigResource

  override protected def beforeAll(): Unit = {
    startCassandra(None, None, systemName, cassandraConfigResource)
    awaitPersistenceInit(system)
    super.beforeAll()
  }

  override protected def afterAll(): Unit = {
    shutdown(system, verifySystemShutdown = true)
    stopCassandra()
    super.afterAll()
  }
}
