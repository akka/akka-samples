package sample.cqrs.grpc

import java.util.UUID

import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.Offset
import akka.persistence.query.PersistenceQuery
import akka.persistence.query.TimeBasedUUID
import akka.persistence.query.scaladsl.EventsByTagQuery
import akka.stream.scaladsl.Source
import sample.cqrs.EventProcessorSettings

class ShoppingCartServiceImpl(system: ActorSystem[_], eventProcessorSettings: EventProcessorSettings)
    extends ShoppingCartService {

  private val eventsByTagQuery =
    PersistenceQuery(system).readJournalFor[EventsByTagQuery](CassandraReadJournal.Identifier)

  override def events(in: EventsRequest): Source[ShoppingCartEventEnvelope, NotUsed] = {
    val tag = eventProcessorSettings.tagPrefix + "-" + in.slice
    val offset =
      if (in.offset == "") Offset.noOffset
      else Offset.timeBasedUUID(UUID.fromString(in.offset))

    eventsByTagQuery.eventsByTag(tag, offset).map { env =>
      val eventOffset = env.offset.asInstanceOf[TimeBasedUUID].value.toString
      ShoppingCartEventEnvelope(offset = eventOffset, event = env.event.toString)
    }
  }
}
