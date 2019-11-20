package sample.sharding.kafka

import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.dynamic.DynamicShardAllocationStrategy
import akka.cluster.sharding.typed.ClusterShardingSettings
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.sharding.typed.scaladsl.Entity
import akka.actor.typed.scaladsl.adapter._
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey

object UserEvents {
  sealed trait Command
  case class UserAction(description: String, replyTo: ActorRef[Done])
      extends Command

  def apply(userId: String): Behavior[Command] = Behaviors.setup { ctx =>
    Behaviors.receiveMessage[Command] {
      case UserAction(desc, ack) =>
        ctx.log.info("user event {}", desc)
        ack.tell(Done)
        Behaviors.same
    }
  }

  val TypeKey: EntityTypeKey[UserEvents.Command] = EntityTypeKey[UserEvents.Command]("user-processing")


  def init(system: ActorSystem[_]): ActorRef[ShardingEnvelope[Command]] = {
      ClusterSharding(system).init(
        Entity(TypeKey)(
          createBehavior = entityContext =>
            UserEvents(entityContext.entityId)
        )
          .withAllocationStrategy(() => new DynamicShardAllocationStrategy(system.toClassic, TypeKey.name))
          .withSettings(ClusterShardingSettings(system))
      )
  }
}
