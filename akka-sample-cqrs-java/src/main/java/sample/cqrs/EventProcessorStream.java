package sample.cqrs;

import akka.Done;
import akka.NotUsed;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.Adapter;
import akka.persistence.cassandra.query.javadsl.CassandraReadJournal;
import akka.persistence.cassandra.session.javadsl.CassandraSession;
import akka.persistence.query.Offset;
import akka.persistence.query.PersistenceQuery;
import akka.persistence.query.TimeBasedUUID;
import akka.persistence.typed.PersistenceId;
import akka.stream.SharedKillSwitch;
import akka.stream.javadsl.RestartSource;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * General purpose event processor infrastructure. Not specific to the ShoppingCart domain.
 */
public abstract class EventProcessorStream<Event> {

  protected final Logger log = LoggerFactory.getLogger(getClass());
  protected final ActorSystem<?> system;
  private final String eventProcessorId;
  protected final String tag;
  private final CassandraReadJournal query;
  private final CassandraSession session;

  protected EventProcessorStream(ActorSystem<?> system, String eventProcessorId, String tag) {
    this.system = system;
    this.eventProcessorId = eventProcessorId;
    this.tag = tag;

    query = PersistenceQuery.get(Adapter.toClassic(system))
      .getReadJournalFor(CassandraReadJournal.class, CassandraReadJournal.Identifier());
    session = CassandraSessionExtension.Id.get(system).session;
  }

  protected abstract CompletionStage<Object> processEvent(Event event, PersistenceId persistenceId, long sequenceNr);

  public void runQueryStream(SharedKillSwitch killSwitch) {
    RestartSource.withBackoff(Duration.ofMillis(500), Duration.ofSeconds(20), 0.1, () ->
      Source.completionStageSource(
        readOffset().thenApply(offset -> {
          log.info("Starting stream for tag [{}] from offset [{}]", tag, offset);
          return processEventsByTag(offset)
            // groupedWithin can be used here to improve performance by reducing number of offset writes,
            // with the trade-off of possibility of more duplicate events when stream is restarted
            .mapAsync(1, this::writeOffset);

        })))
      .via(killSwitch.flow())
      .runWith(Sink.ignore(), system);
  }

  @SuppressWarnings("unchecked")
  private Source<Offset, NotUsed> processEventsByTag(Offset offset) {
    return query
      .eventsByTag(tag, offset)
      .mapAsync(1, eventEnvelope -> {
        return processEvent((Event) eventEnvelope.event(), PersistenceId.ofUniqueId(eventEnvelope.persistenceId()), eventEnvelope.sequenceNr())
          .thenApply(done -> eventEnvelope.offset());
      });
  }

  private CompletionStage<PreparedStatement> prepareWriteOffset() {
    return session.prepare("INSERT INTO akka_cqrs_sample.offsetStore (eventProcessorId, tag, timeUuidOffset) VALUES (?, ?, ?)");
  }

  private CompletionStage<Done> writeOffset(Offset offset) {
    if (offset instanceof TimeBasedUUID) {
      UUID uuidOffset = ((TimeBasedUUID) offset).value();
      return prepareWriteOffset()
        .thenApply(stmt -> stmt.bind(eventProcessorId, tag, uuidOffset))
        .thenCompose(session::executeWrite);
    } else {
      throw new IllegalArgumentException("Unexpected offset type " + offset);
    }
  }


  private CompletionStage<Offset> readOffset() {
    return session.selectOne(
        "SELECT timeUuidOffset FROM akka_cqrs_sample.offsetStore WHERE eventProcessorId = ? AND tag = ?",
        eventProcessorId,
        tag)
      .thenApply(this::extractOffset);
  }

  private Offset extractOffset(Optional<Row> maybeRow) {
      if (maybeRow.isPresent()) {
        UUID uuid = maybeRow.get().getUUID("timeUuidOffset");
        if (uuid == null) {
          return Offset.noOffset();
        } else {
          return Offset.timeBasedUUID(uuid);
        }
      } else {
        return Offset.noOffset();
    }
  }
}
