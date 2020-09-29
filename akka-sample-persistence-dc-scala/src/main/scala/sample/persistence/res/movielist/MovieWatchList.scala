package sample.persistence.res.movielist

import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.cassandra.query.javadsl.CassandraReadJournal
import akka.persistence.typed.{PersistenceId, ReplicaId, ReplicationId}
import akka.persistence.typed.crdt.ORSet
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplicatedEventSourcing}
import sample.persistence.res.MainApp

/**
 * The MovieWatchList shows how to use a built in CRDT as the state.
 *
 * An [[akka.persistence.typed.crdt.ORSet]] is used to represent a list of movies a user has watched.
 */
object MovieWatchList {

  sealed trait Command

  final case class AddMovie(movieId: String) extends Command
  final case class RemoveMovie(movieId: String) extends Command
  final case class GetMovieList(replyTo: ActorRef[MovieList]) extends Command
  final case class MovieList(movieIds: Set[String])

  def apply(entityId: String, replicaId: ReplicaId): Behavior[Command] = {
    ReplicatedEventSourcing.commonJournalConfig(
      ReplicationId("movies", entityId, replicaId),
      MainApp.AllReplicas,
      CassandraReadJournal.Identifier
    )(replicationContext => eventSourcedBehavior(replicaId, replicationContext.persistenceId))
  }

  private def eventSourcedBehavior(replicaId: ReplicaId, persistenceId: PersistenceId): EventSourcedBehavior[Command, ORSet.DeltaOp, ORSet[String]] =
    EventSourcedBehavior[Command, ORSet.DeltaOp, ORSet[String]](
      persistenceId,
      ORSet.empty(replicaId),
      (state, cmd) => commandHandler(state, cmd),
      (state, event) => state.applyOperation(event))

  private def commandHandler(state: ORSet[String], cmd: Command): Effect[ORSet.DeltaOp, ORSet[String]] = {
    // operations on an ORSet don't but instead create events describing the change that
    // are then persisted
    cmd match {
      case AddMovie(movieId) =>
        Effect.persist(state.add(movieId))
      case RemoveMovie(movieId) =>
        Effect.persist(state.remove(movieId))
      case GetMovieList(replyTo) =>
        replyTo ! MovieList(state.elements)
        Effect.none
    }
  }

}











