package sample.persistence.multidc

import akka.Done
import akka.actor.Props
import akka.cluster.sharding.ShardRegion
import akka.cluster.sharding.ShardRegion.StartEntity
import akka.persistence.multidc.PersistenceMultiDcSettings
import akka.persistence.multidc.SpeculativeReplicatedEvent
import akka.persistence.multidc.scaladsl.ReplicatedEntity;

object ThumbsUpCounter {
  sealed trait Command {
    def resourceId: String
  }

  final case class GiveThumbsUp(resourceId: String, userId: String) extends Command

  final case class GetCount(resourceId: String) extends Command

  final case class GetUsers(resourceId: String) extends Command

  sealed trait Event

  final case class GaveThumbsUp(userId: String) extends Event

  final case class State(users: Set[String]) {
    def add(userId: String): State = copy(users + userId)
  }

  def shardingProps(settings: PersistenceMultiDcSettings): Props =
    ReplicatedEntity.clusterShardingProps(ShardingTypeName, () => new ThumbsUpCounter, settings)

  val ShardingTypeName = "counter"

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case cmd: Command => (cmd.resourceId, cmd)
    case evt: SpeculativeReplicatedEvent => (evt.entityId, evt)
  }

  val MaxShards = 100
  def shardId(entityId: String): String = (math.abs(entityId.hashCode) % MaxShards).toString
  val extractShardId: ShardRegion.ExtractShardId = {
    case cmd: Command   => shardId(cmd.resourceId)
    case evt: SpeculativeReplicatedEvent => shardId(evt.entityId)
    case StartEntity(entityId)           => shardId(entityId)
  }
}

class ThumbsUpCounter
  extends ReplicatedEntity[ThumbsUpCounter.Command, ThumbsUpCounter.Event, ThumbsUpCounter.State] {
  import ThumbsUpCounter._

  override def initialState: State = State(Set.empty)

  override def commandHandler: CommandHandler = CommandHandler { (ctx, state, cmd) =>
    cmd match {
      case GiveThumbsUp(_, userId) =>
        Effect.persist(GaveThumbsUp(userId)).andThen { state2 =>
          log.info("Thumbs-up by {}, total count {}", userId, state2.users.size)
          ctx.sender() ! state2.users.size
        }
      case GetCount(_) =>
        ctx.sender() ! state.users.size
        Effect.none
      case GetUsers(_) =>
        ctx.sender() ! state
        Effect.none
    }
  }

  override def eventHandler(state: State, event: Event): State = {
    event match {
      case GaveThumbsUp(userId) => state.add(userId)
    }
  }

}
