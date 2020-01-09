package sample.cqrs

import akka.actor.typed.scaladsl.LoggerOps
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.Done
import akka.actor.typed.ActorSystem
import akka.actor.typed.eventstream.EventStream
import akka.persistence.typed.PersistenceId

class ShoppingCartEventProcessorStream(
    system: ActorSystem[_],
    executionContext: ExecutionContext,
    eventProcessorId: String,
    tag: String)
    extends EventProcessorStream[ShoppingCart.Event](system, executionContext, eventProcessorId, tag) {

  def processEvent(event: ShoppingCart.Event, persistenceId: PersistenceId, sequenceNr: Long): Future[Done] = {
    log.infoN("EventProcessor({}) consumed {} from {} with seqNr {}", tag, event, persistenceId, sequenceNr)
    event match {
      case ShoppingCart.ItemAdded(cartId, itemId, quantity, timestamp) =>
        log.infoN(
          "Event latency of {} ItemAdded {} {}: {} ms",
          cartId,
          itemId,
          quantity,
          System.currentTimeMillis() - timestamp)
      case _ =>
    }

    system.eventStream ! EventStream.Publish(event)
    Future.successful(Done)
  }
}
