package sample.cqrs

import akka.Done
import akka.actor.{Actor, ActorLogging, Props}
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.{NoOffset, Offset, PersistenceQuery, TimeBasedUUID}
import akka.stream.KillSwitches
import akka.stream.scaladsl.{RestartSource, Sink, Source}
import com.datastax.driver.core.{PreparedStatement, Row}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

object EventProcessor {
  def props: Props =
    Props(new EventProcessor)
}

class EventProcessor extends Actor with ActorLogging {

  private val settings = Settings(context.system)
  private val eventProcessorId = settings.eventProcessorSettings.id
  private val tag = self.path.name

  private val session = CassandraSessionExtension(context.system).session
  private implicit val materializer = CassandraSessionExtension(context.system).materializer
  private implicit val ec: ExecutionContext = context.dispatcher
  private val query = PersistenceQuery(context.system).readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)
  private val killSwitch = KillSwitches.shared("eventProcessorSwitch")
  override val log = super.log // eager initialization because used from inside stream

  override def preStart(): Unit = {
    super.preStart()
    runQueryStream()
  }

  override def postStop(): Unit = {
    super.postStop()
    killSwitch.shutdown()
  }

  def receive = {
    case KeepAlive.Ping =>
      sender() ! KeepAlive.Pong
      //log.info(s"Event processor(${self.path.name}) @ ${context.system.settings.config.getString("akka.remote.artery.canonical.hostname")}:${context.system.settings.config.getInt("akka.remote.artery.canonical.port")}")

    case message =>
      log.error("Got unexpected message: {}", message)
  }

  private def runQueryStream(): Unit = {
    RestartSource.withBackoff(minBackoff = 500.millis, maxBackoff = 20.seconds, randomFactor = 0.1) { () =>
      Source.fromFutureSource {
        readOffset().map { offset =>
          log.info("Starting stream for tag [{}] from offset [{}]", tag, offset)
          query.eventsByTag(tag, offset)
            .map { eventEnvelope =>
              println(s"#Eventprocessor($tag) got ${eventEnvelope}") // You would write to Kafka here
              eventEnvelope.offset
            }
            .mapAsync(1)(writeOffset)
        }
      }
    }
    .via(killSwitch.flow)
    .runWith(Sink.ignore)
  }

  private def readOffset(): Future[Offset] = {
    session.selectOne(
      s"SELECT timeUuidOffset FROM akka_cqrs_sample.offsetStore WHERE eventProcessorId = ? AND tag = ?",
      eventProcessorId, tag
    ).map(extractOffset)
  }

  private def extractOffset(maybeRow: Option[Row]): Offset = {
    maybeRow match {
      case Some(row) =>
        val uuid = row.getUUID("timeUuidOffset")
        if (uuid == null) {
          NoOffset
        } else {
          TimeBasedUUID(uuid)
        }
      case None => NoOffset
    }
  }

  private def prepareWriteOffset: Future[PreparedStatement] = {
    session.prepare("INSERT INTO akka_cqrs_sample.offsetStore (eventProcessorId, tag, timeUuidOffset) VALUES (?, ?, ?)")
  }

  private def writeOffset(offset: Offset): Future[Done] = {
    offset match {
      case t: TimeBasedUUID =>
        prepareWriteOffset.map(stmt => stmt.bind(eventProcessorId, tag, t.value)).flatMap { boundStmt =>
          session.executeWrite(boundStmt)
        }

      case _ =>
        throw new IllegalArgumentException(s"Unexpected offset type $offset")
    }

  }

}
