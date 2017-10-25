package akka.persistence.multidc.testkit

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.persistence.multidc.PersistenceMultiDcSettings
import akka.persistence.query.PersistenceQuery
import akka.testkit.ImplicitSender
import akka.testkit.TestKit
import akka.testkit.TestProbe

import org.scalatest.BeforeAndAfterAll
import org.scalatest.Matchers
import org.scalatest.WordSpecLike
import org.scalatest.BeforeAndAfter

import akka.persistence.multidc.testkit._
import akka.persistence.multidc.internal.CassandraReplicatedEventQuery
import akka.persistence.multidc.internal.ReplicatedEventEnvelope

object BaseSpec {
  val clusterConfig = ConfigFactory.parseString("""
    akka.actor.provider = "cluster"
    akka.remote.netty.tcp.port = 0
    akka.remote.artery.canonical.port = 0
    akka.remote.artery.canonical.hostname = 127.0.0.1
    akka.cluster.jmx.multi-mbeans-in-same-jvm = on

    # inceasing probability due to issue https://github.com/akka/akka/issues/23803
    akka.cluster.multi-data-center.cross-data-center-gossip-probability = 0.5

    # speed up joining and such
    akka.cluster.gossip-interval = 500 ms
  """)

  def createFirstSystem(name: String): ActorSystem = createFirstSystem(name, ConfigFactory.empty())
  def createFirstSystem(name: String, cfg: Config): ActorSystem = ActorSystem(
    name,
    cfg.withFallback(ConfigFactory.parseString(s"""
    akka.loglevel = INFO
    akka.cluster.multi-data-center.self-data-center = DC-A
    akka.persistence.multi-data-center.all-data-centers = [ DC-A, DC-B, DC-C ]
    cassandra-journal-multi-dc.keyspace=$name
    cassandra-snapshot-store.keyspace=${name}Snapshot
    cassandra-query-journal-multi-dc.class = "${classOf[InterruptableCassandraReadJournalProvider].getName}"
    """)).withFallback(BaseSpec.clusterConfig).withFallback(CassandraLifecycle.config))

  def otherSystemSettings: Config =
    ConfigFactory.parseString("""
        akka.cluster.multi-data-center.self-data-center = DC-B
        akka.persistence.multi-data-center.all-data-centers = [ DC-A, DC-B, DC-C ]
        """)

  def createOtherSystem(firstSystem: ActorSystem) =
    ActorSystem(
      firstSystem.name,
      ConfigFactory.parseString("""
        akka.cluster.multi-data-center.self-data-center = DC-B
        akka.persistence.multi-data-center.all-data-centers = [ DC-A, DC-B, DC-C ]
        """).withFallback(firstSystem.settings.config))

  def createThirdSystem(firstSystem: ActorSystem) =
    ActorSystem(
      firstSystem.name,
      ConfigFactory.parseString("""
        akka.cluster.multi-data-center.self-data-center = DC-C
        akka.persistence.multi-data-center.all-data-centers = [ DC-A, DC-B, DC-C ]
        """).withFallback(firstSystem.settings.config))

}

// TODO move a lot of this to a trait in testkit?
abstract class BaseSpec(name: String, cfg: Config = ConfigFactory.empty)
  extends TestKit(BaseSpec.createFirstSystem(name, cfg))
  with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll with BeforeAndAfter with CassandraLifecycleScalatest {
  import BaseSpec._

  val otherSystem = createOtherSystem(system)
  val thirdSystem = createThirdSystem(system)

  val settings = PersistenceMultiDcSettings(system)
  val otherSettings = PersistenceMultiDcSettings(otherSystem)
  val thirdSettings = PersistenceMultiDcSettings(thirdSystem)

  private def queries(sys: ActorSystem): InterruptableCassandraReplicatedEventQuery =
    PersistenceQuery(sys).readJournalFor[InterruptableCassandraReplicatedEventQuery](CassandraReplicatedEventQuery.Identifier)

  def disableReplication(from: ActorSystem, to: ActorSystem) =
    queries(to).disableReplication(Cluster(from).selfDataCenter)

  def enableReplication(from: ActorSystem, to: ActorSystem) =
    queries(to).enableReplication(Cluster(from).selfDataCenter)

  private[multidc] def addErrorFilter(sys: ActorSystem, key: String)(f: ReplicatedEventEnvelope => Option[Throwable]): Unit =
    queries(sys).addErrorFilter(key)(f)

  def removeErrorFilter(sys: ActorSystem, key: String): Unit =
    queries(sys).removeErrorFilter(key)

  override def systemName: String = name

  after {
    Seq(system, otherSystem, thirdSystem).foreach { sys =>
      queries(sys).enableAll()
    }
  }

  override protected def afterAll(): Unit = {
    shutdown(otherSystem)
    shutdown(thirdSystem)
    shutdown()
    super.afterAll()
  }

  def stopA(ref: ActorRef): Unit =
    stop(ref, system)

  def stopB(ref: ActorRef): Unit =
    stop(ref, otherSystem)

  def stop(ref: ActorRef, sys: ActorSystem): Unit = {
    val probe = TestProbe()(sys)
    probe.watch(ref)
    sys.stop(ref)
    probe.expectTerminated(ref)
  }

}
