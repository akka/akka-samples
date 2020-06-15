package sample.cqrs;

import akka.Done;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.eventstream.EventStream;
import akka.projection.eventsourced.EventEnvelope;
import akka.projection.javadsl.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class ShoppingCartProjectionHandler extends Handler<EventEnvelope<ShoppingCart.Event>> {

  private final Logger log = LoggerFactory.getLogger(getClass());
  private final ActorSystem<?> system;
  private final String tag;

  public ShoppingCartProjectionHandler(ActorSystem<?> system, String tag) {
    this.system = system;
    this.tag = tag;
  }

  @Override
  public CompletionStage<Done> process(EventEnvelope<ShoppingCart.Event> envelope) {
    log.info("EventProcessor({}) consumed {} from {} with seqNr {}", tag, envelope.event(), envelope.persistenceId(), envelope.sequenceNr());
    system.eventStream().tell(new EventStream.Publish<>(envelope.event()));
    return CompletableFuture.completedFuture(Done.getInstance());
  }

}
