package sample.cqrs

import scala.concurrent.Future

import akka.Done
import akka.actor.typed.ActorSystem
import akka.actor.typed.Extension
import akka.actor.typed.ExtensionId
import akka.actor.typed.scaladsl.adapter._
import akka.event.Logging
import akka.persistence.cassandra.ConfigSessionProvider
import akka.persistence.cassandra.session.CassandraSessionSettings
import akka.persistence.cassandra.session.scaladsl.CassandraSession

object CassandraSessionExtension extends ExtensionId[CassandraSessionExtension] {

  def get(system: ActorSystem[_]): CassandraSessionExtension = apply(system)

  override def createExtension(system: ActorSystem[_]): CassandraSessionExtension =
    new CassandraSessionExtension(system)

}

class CassandraSessionExtension(system: ActorSystem[_]) extends Extension {

  val session: CassandraSession = {
    val sessionConfig = system.settings.config.getConfig("cassandra-journal")
    new CassandraSession(
      system.toClassic,
      new ConfigSessionProvider(system.toClassic, sessionConfig),
      CassandraSessionSettings(sessionConfig),
      system.executionContext,
      Logging(system.toClassic, getClass),
      metricsCategory = "sample",
      init = _ => Future.successful(Done))
  }

}
