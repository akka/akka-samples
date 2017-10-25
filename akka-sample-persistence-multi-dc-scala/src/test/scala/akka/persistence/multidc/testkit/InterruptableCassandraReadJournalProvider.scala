/**
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.persistence.multidc.testkit

import scala.annotation.tailrec
import scala.concurrent.Future

import com.typesafe.config.Config

import akka.NotUsed
import akka.actor.ExtendedActorSystem
import akka.actor.ExtendedActorSystem
import akka.annotation.InternalApi
import akka.event.Logging
import akka.persistence.query.ReadJournalProvider
import akka.persistence.cassandra.query.EventsByPersistenceIdStage
import akka.persistence.multidc.internal.CassandraReplicatedEventQuery
import akka.persistence.multidc.internal.ReplicatedEventEnvelope
import akka.stream.contrib.{ SwitchMode, Valve, ValveSwitch }
import akka.stream.scaladsl.{ Keep, Source }

@InternalApi private[testkit] class InterruptableCassandraReadJournalProvider(system: ExtendedActorSystem, config: Config) extends ReadJournalProvider {

  override val scaladslReadJournal: CassandraReplicatedEventQuery =
    new InterruptableCassandraReplicatedEventQuery(system, config)

  override val javadslReadJournal: CassandraReplicatedEventQuery =
    scaladslReadJournal

}

/**
 * It is retrieved with:
 * {{{
 * val queries = PersistenceQuery(system).readJournalFor[InterruptableCassandraReplicatedEventQuery](CassandraReplicatedEventQeury.Identifier)
 * }}}
 */
@InternalApi private[akka] class InterruptableCassandraReplicatedEventQuery(system: ExtendedActorSystem, config: Config)
  extends CassandraReplicatedEventQuery(system, config) {

  private val log = Logging(system, getClass)
  private var sourcesByDc = Map.empty[String, List[ValveSwitch]]
  private var pendingDisable = Set.empty[String]
  private var errors = Map.empty[String, ReplicatedEventEnvelope => Option[Throwable]]

  override def replicatedEvents(persistenceId: String, fromDc: String, sequenceNr: Long): Source[ReplicatedEventEnvelope, Future[EventsByPersistenceIdStage.Control]] = {
    super.replicatedEvents(persistenceId, fromDc, sequenceNr)
      .viaMat(new Valve(SwitchMode.Open))(Keep.both)
      .map { e =>
        errors.foreach { case (_, f) => f(e).foreach(throw _) }
        e
      }
      .mapMaterializedValue {
        case (control, fs) =>
          add(fromDc, fs)
          control
      }
  }

  def disableReplication(dc: String) = synchronized {
    sourcesByDc.get(dc) match {
      case Some(values) => values.foreach(_.flip(SwitchMode.Close))
      case None         => pendingDisable += dc // not started yet
    }
  }

  def enableReplication(dc: String) = synchronized {
    sourcesByDc.get(dc) match {
      case Some(values) => values.foreach(_.flip(SwitchMode.Open))
      case None         => pendingDisable -= dc
    }

  }

  def enableAll(): Unit = synchronized {
    sourcesByDc.keys.foreach(enableReplication)
    pendingDisable = Set.empty
  }

  private def add(dc: String, fs: Future[ValveSwitch]): Unit = {
    implicit val ec = system.dispatcher
    fs.onSuccess { case switch => add(dc, switch) }
  }

  private def add(dc: String, switch: ValveSwitch): Unit = synchronized {
    val oldList = sourcesByDc.getOrElse(dc, Nil)
    sourcesByDc = sourcesByDc.updated(dc, switch :: oldList)
    if (pendingDisable(dc)) {
      switch.flip(SwitchMode.Close)
      pendingDisable -= dc
    }
  }

  def addErrorFilter(key: String)(f: ReplicatedEventEnvelope => Option[Throwable]): Unit = synchronized {
    errors = errors.updated(key, f)
  }

  def removeErrorFilter(key: String): Unit = synchronized {
    errors = errors - key
  }

  def removeAllErrorFilters(): Unit = synchronized {
    errors = Map.empty
  }

}

