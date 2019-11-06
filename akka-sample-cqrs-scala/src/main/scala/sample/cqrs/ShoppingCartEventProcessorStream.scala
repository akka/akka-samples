package sample.cqrs

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
    log.info("EventProcessor({}) consumed {} from {} with seqNr {}", tag, event, persistenceId, sequenceNr)
    system.eventStream ! EventStream.Publish(event)
    Future.successful(Done)
  }
}
