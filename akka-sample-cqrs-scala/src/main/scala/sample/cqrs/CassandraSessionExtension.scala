package sample.cqrs

import scala.concurrent.Future

import akka.Done
import akka.actor.ActorSystem
import akka.actor.ExtendedActorSystem
import akka.actor.Extension
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import akka.event.Logging
import akka.persistence.cassandra.ConfigSessionProvider
import akka.persistence.cassandra.session.CassandraSessionSettings
import akka.persistence.cassandra.session.scaladsl.CassandraSession
import akka.stream.ActorMaterializer
import akka.stream.Materializer

object CassandraSessionExtension extends ExtensionId[CassandraSessionExtension] with ExtensionIdProvider {

  override def get(system: ActorSystem): CassandraSessionExtension = super.get(system)

  override def lookup = CassandraSessionExtension

  override def createExtension(system: ExtendedActorSystem): CassandraSessionExtension =
    new CassandraSessionExtension(system)

}

class CassandraSessionExtension(system: ActorSystem) extends Extension {

  private val log = Logging(system, getClass)

  val session: CassandraSession = {
    val sessionConfig = system.settings.config.getConfig("cassandra-journal")
    new CassandraSession(system,
      new ConfigSessionProvider(system, sessionConfig),
      CassandraSessionSettings(sessionConfig),
      system.dispatcher,
      log,
      metricsCategory = "sample",
      init = _ => Future.successful(Done)
    )
  }

  val materializer: Materializer = ActorMaterializer()(system)

}
