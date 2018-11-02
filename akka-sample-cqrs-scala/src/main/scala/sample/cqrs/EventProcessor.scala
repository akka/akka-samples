package sample.cqrs

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.Done
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.NoOffset
import akka.persistence.query.Offset
import akka.persistence.query.PersistenceQuery
import akka.persistence.query.TimeBasedUUID
import akka.stream.KillSwitches
import akka.stream.scaladsl.RestartSource
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import com.datastax.driver.core.PreparedStatement
import com.datastax.driver.core.Row

object EventProcessor {
  def props(tag: String): Props =
    Props(new EventProcessor(tag))
}

class EventProcessor(tag: String) extends Actor with ActorLogging {
  private val eventProcessorId = context.self.path.name

  private val session = CassandraSessionExtension(context.system).session
  private implicit val materializer = CassandraSessionExtension(context.system).materializer
  private implicit val ec: ExecutionContext = context.dispatcher
  private val query = PersistenceQuery(context.system).readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)
  private val killSwitch = KillSwitches.shared("eventProcessorSwitch")
  override val log = super.log // eager intialization because used from inside stream

  override def preStart(): Unit = {
    super.preStart()
    runQueryStream()
  }

  override def postStop(): Unit = {
    super.postStop()
    killSwitch.shutdown()
  }

  def receive = {
    case message =>
      log.info("Got message: {}", message)
  }

  private def runQueryStream(): Unit = {
    RestartSource.withBackoff(minBackoff = 500.millis, maxBackoff = 20.seconds, randomFactor = 0.1) { () =>
      Source.fromFutureSource {
        readOffset().map { offset =>
          log.info("Staring stream for tag [{}] from offset [{}]", tag, offset)
          query.eventsByTag(tag, offset)
            .map { eventEnvelope =>
              println(s"# got $eventEnvelope") // FIXME
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
