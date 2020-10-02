package akka.projection.testing

import akka.actor.typed.ActorSystem
import akka.projection.eventsourced.EventEnvelope
import akka.projection.jdbc.scaladsl.JdbcHandler
import org.slf4j.{Logger, LoggerFactory}

class ProjectionHandler(tag: String, system: ActorSystem[_])
    extends JdbcHandler[EventEnvelope[ConfigurablePersistentActor.Event], HikariJdbcSession] {
  private val log: Logger = LoggerFactory.getLogger(getClass)

  override def process(session: HikariJdbcSession, envelope: EventEnvelope[ConfigurablePersistentActor.Event]): Unit = {
    log.info("Event {} for tag {}", envelope.event.payload, tag)
    session.withConnection(connection =>
      connection.createStatement()
        .execute(s"insert into events(name, event) values ('${envelope.event.testName}','${envelope.event.payload}')")
    )
  }
}
