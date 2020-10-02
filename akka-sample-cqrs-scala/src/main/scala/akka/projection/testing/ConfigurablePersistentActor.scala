package akka.projection.testing

import akka.Done
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

object ConfigurablePersistentActor {

  val Key: EntityTypeKey[Command] = EntityTypeKey[Command]("configurable")

  def init(settings: EventProcessorSettings, system: ActorSystem[_]): ActorRef[ShardingEnvelope[Command]] = {
    ClusterSharding(system).init(Entity(Key)(ctx => apply(settings, ctx.entityId))
      .withRole("write-model"))
  }

  trait Command

  final case class PersistAndAck(toPersist: String, replyTo: ActorRef[StatusReply[Done]], testName: String) extends Command

  final case class Persist(toPersist: String, testName: String) extends Command

  final case class Event(testName: String, payload: String, timeCreated: Long = System.currentTimeMillis()) extends CborSerializable

  final case class State(eventsProcessed: Long) extends CborSerializable

  def apply(settings: EventProcessorSettings, persistenceId: String): Behavior[Command] =
    Behaviors.setup { ctx =>
      EventSourcedBehavior[Command, Event, State](
        persistenceId = PersistenceId.ofUniqueId(persistenceId),
        State(0),
        (_, command) => command match {
          case Persist(toPersist, testName) =>
            Effect.persist(Event(testName, toPersist))
          case PersistAndAck(toPersist, ack, testName) =>
            ctx.log.info("persisting event {}", command)
            Effect.persist(Event(testName, toPersist)).thenRun(_ => ack ! StatusReply.ack())
        },
        (state, _) => state.copy(eventsProcessed = state.eventsProcessed + 1)).withTagger(event =>
        Set("tag-" + math.abs(event.hashCode() % settings.parallelism)))
    }

}
