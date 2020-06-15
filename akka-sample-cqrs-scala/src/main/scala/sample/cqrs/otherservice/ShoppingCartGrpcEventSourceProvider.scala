package sample.cqrs.otherservice

import java.util.UUID

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.actor.typed.ActorSystem
import akka.persistence.query.Offset
import akka.persistence.query.TimeBasedUUID
import akka.projection.scaladsl.SourceProvider
import akka.stream.scaladsl.Source
import sample.cqrs.grpc.EventsRequest
import sample.cqrs.grpc.ShoppingCartEventEnvelope
import sample.cqrs.grpc.ShoppingCartServiceClient

class ShoppingCartGrpcEventSourceProvider(client: ShoppingCartServiceClient, system: ActorSystem[_], slice: Int)
    extends SourceProvider[Offset, ShoppingCartEventEnvelope] {
  implicit private val ec: ExecutionContext = system.executionContext

  override def source(offset: () => Future[Option[Offset]]): Future[Source[ShoppingCartEventEnvelope, _]] = {
    val offsetStr = offset().map {
      case None                        => ""
      case Some(offset: TimeBasedUUID) => offset.value.toString
      case Some(other)                 => throw new IllegalArgumentException(s"Unexpected offset type: $other")
    }
    offsetStr.map(o => client.events(EventsRequest(slice, o)))
  }

  override def extractOffset(envelope: ShoppingCartEventEnvelope): Offset = {
    Offset.timeBasedUUID(UUID.fromString(envelope.offset))
  }
}
