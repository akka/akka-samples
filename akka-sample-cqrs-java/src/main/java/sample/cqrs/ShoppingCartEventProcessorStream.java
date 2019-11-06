package sample.cqrs;

import akka.Done;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.eventstream.EventStream;
import akka.persistence.typed.PersistenceId;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class ShoppingCartEventProcessorStream extends EventProcessorStream<ShoppingCart.Event>{

  public ShoppingCartEventProcessorStream(ActorSystem<?> system, String eventProcessorId, String tag) {
    super(system, eventProcessorId, tag);
  }

  @Override
  protected CompletionStage<Object> processEvent(ShoppingCart.Event event, PersistenceId persistenceId, long sequenceNr) {
    log.info("EventProcessor({}) consumed {} from {} with seqNr {}", tag, event, persistenceId, sequenceNr);
    system.eventStream().tell(new EventStream.Publish<>(event));
    return CompletableFuture.completedFuture(Done.getInstance());
  }
}
