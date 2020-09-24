package sample.persistence.res.counter

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.ReplicatedEntityProvider
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplicatedEventSourcing}
import akka.persistence.typed.{ReplicaId, ReplicationId}
import sample.persistence.res.{CborSerializable, MainApp}

object ThumbsUpCounter {

  // sent over sharding
  sealed trait Command extends CborSerializable {
    def resourceId: String
  }

  final case class GiveThumbsUp(resourceId: String, userId: String, replyTo: ActorRef[Long]) extends Command
  final case class GetCount(resourceId: String, replyTo: ActorRef[Long]) extends Command
  final case class GetUsers(resourceId: String, replyTo: ActorRef[State]) extends Command

  // saved to DB
  sealed trait Event extends CborSerializable
  final case class GaveThumbsUp(userId: String) extends Event

  // saved to DB
  final case class State(users: Set[String]) extends CborSerializable {
    def add(userId: String): State = copy(users + userId)
  }

  val Provider: ReplicatedEntityProvider[Command] = ReplicatedEntityProvider.perDataCenter("counter", MainApp.AllReplicas) { replicationId => ThumbsUpCounter(replicationId) }

  // we use a shared journal as cassandra typically spans DCs rather than a DB per replica
  def apply(replicationId: ReplicationId): Behavior[Command] =
    Behaviors.setup { ctx =>
      ReplicatedEventSourcing.commonJournalConfig(replicationId, Replicas, CassandraReadJournal.Identifier) { replicationContext =>
        EventSourcedBehavior[Command, Event, State](
          persistenceId = replicationId.persistenceId,
          emptyState = State(Set.empty),
          commandHandler = (state, cmd) => cmd match {
            case GiveThumbsUp(_, userId, replyTo) =>
              Effect.persist(GaveThumbsUp(userId)).thenRun { state2 =>
                ctx.log.info("Thumbs-up by {}, total count {}", userId, state2.users.size)
                replyTo ! state2.users.size
              }
            case GetCount(_, replyTo) =>
              replyTo ! state.users.size
              Effect.none
            case GetUsers(_, replyTo) =>
              replyTo ! state
              Effect.none
          },
          eventHandler = (state, event) => event match {
            case GaveThumbsUp(userId) =>
              state.add(userId)
          }
        )
      }
    }
}
