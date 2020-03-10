package sample.cqrs

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.reflect.ClassTag
import akka.Done
import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.PostStop
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardedDaemonProcessSettings}
import akka.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import akka.cluster.typed.Cluster
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.Offset
import akka.persistence.query.PersistenceQuery
import akka.persistence.query.TimeBasedUUID
import akka.persistence.typed.PersistenceId
import akka.stream.KillSwitches
import akka.stream.SharedKillSwitch
import akka.stream.scaladsl.RestartSource
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import com.datastax.driver.core.PreparedStatement
import com.datastax.driver.core.Row
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * General purpose event processor infrastructure. Not specific to the ShoppingCart domain.
 */
object EventProcessor {

  case object Ping extends CborSerializable

  def init[Event](
      system: ActorSystem[_],
      settings: EventProcessorSettings,
      eventProcessorStream: String => EventProcessorStream[Event]): Unit = {
      val shardedDaemonSettings = ShardedDaemonProcessSettings(system)
          .withKeepAliveInterval(settings.keepAliveInterval)
          .withShardingSettings(ClusterShardingSettings(system).withRole("read-model"))
      ShardedDaemonProcess(system)
        .init(s"event-processors-${settings.id}", settings.parallelism, i => EventProcessor(eventProcessorStream(s"${settings.tagPrefix}-$i")), shardedDaemonSettings, None)
  }

  def apply(eventProcessorStream: EventProcessorStream[_]): Behavior[Ping.type] = {

    Behaviors.setup { ctx  =>

      ctx.log.info("Event processor running {}", eventProcessorStream)
      val killSwitch = KillSwitches.shared("eventProcessorSwitch")
      eventProcessorStream.runQueryStream(killSwitch)
      Behaviors
        .receiveMessage[Ping.type] { _ =>
          Behaviors.same
        }
        .receiveSignal {
          case (_, PostStop) =>
            killSwitch.shutdown()
            Behaviors.same
        }
    }
  }

}

abstract class EventProcessorStream[Event: ClassTag](
    system: ActorSystem[_],
    executionContext: ExecutionContext,
    eventProcessorId: String,
    tag: String) {

  protected val log: Logger = LoggerFactory.getLogger(getClass)
  implicit val sys: ActorSystem[_] = system
  implicit val ec: ExecutionContext = executionContext

  private val query =
    PersistenceQuery(system.toClassic).readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)
  private val session = CassandraSessionExtension(system).session

  protected def processEvent(event: Event, persistenceId: PersistenceId, sequenceNr: Long): Future[Done]

  def runQueryStream(killSwitch: SharedKillSwitch): Unit = {
    RestartSource
      .withBackoff(minBackoff = 500.millis, maxBackoff = 20.seconds, randomFactor = 0.1) { () =>
        Source.futureSource {
          readOffset().map { offset =>
            log.info("Starting stream for tag [{}] from offset [{}]", tag, offset)
            processEventsByTag(offset)
            // groupedWithin can be used here to improve performance by reducing number of offset writes,
            // with the trade-off of possibility of more duplicate events when stream is restarted
              .mapAsync(1)(writeOffset)
          }
        }
      }
      .via(killSwitch.flow)
      .runWith(Sink.ignore)
  }

  private def processEventsByTag(offset: Offset): Source[Offset, NotUsed] = {
    query.eventsByTag(tag, offset).mapAsync(1) { eventEnvelope =>
      eventEnvelope.event match {
        case event: Event =>
          processEvent(event, PersistenceId.ofUniqueId(eventEnvelope.persistenceId), eventEnvelope.sequenceNr).map(_ =>
            eventEnvelope.offset)
        case other =>
          Future.failed(new IllegalArgumentException(s"Unexpected event [${other.getClass.getName}]"))
      }
    }
  }

  private def readOffset(): Future[Offset] = {
    session
      .selectOne(
        "SELECT timeUuidOffset FROM akka_cqrs_sample.offsetStore WHERE eventProcessorId = ? AND tag = ?",
        eventProcessorId,
        tag)
      .map(extractOffset)
  }

  private def extractOffset(maybeRow: Option[Row]): Offset = {
    maybeRow match {
      case Some(row) =>
        val uuid = row.getUUID("timeUuidOffset")
        if (uuid == null) {
          startOffset()
        } else {
          TimeBasedUUID(uuid)
        }
      case None => startOffset()
    }
  }

  // start looking from one week back if no offset was stored
  private def startOffset(): Offset = {
    query.timeBasedUUIDFrom(System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000))
  }

  private def prepareWriteOffset(): Future[PreparedStatement] = {
    session.prepare("INSERT INTO akka_cqrs_sample.offsetStore (eventProcessorId, tag, timeUuidOffset) VALUES (?, ?, ?)")
  }

  private def writeOffset(offset: Offset)(implicit ec: ExecutionContext): Future[Done] = {
    offset match {
      case t: TimeBasedUUID =>
        prepareWriteOffset().map(stmt => stmt.bind(eventProcessorId, tag, t.value)).flatMap { boundStmt =>
          session.executeWrite(boundStmt)
        }

      case _ =>
        throw new IllegalArgumentException(s"Unexpected offset type $offset")
    }

  }

}
