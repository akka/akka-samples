package sample.cqrs.otherservice

import scala.concurrent.Future

import akka.Done
import akka.projection.scaladsl.Handler
import org.slf4j.LoggerFactory
import sample.cqrs.grpc.ShoppingCartEventEnvelope

class ShoppingCartEventsProjectionHandler(slice: Int) extends Handler[ShoppingCartEventEnvelope] {
  private val log = LoggerFactory.getLogger(getClass)

  override def process(envelope: ShoppingCartEventEnvelope): Future[Done] = {
    log.info("OtherService slice {} consumed event {} with offset {}", slice, envelope.event, envelope.offset)

    // This would store a representation in the domain of OtherService from the consumed shopping cart events

    Future.successful(Done)
  }
}
