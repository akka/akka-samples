package sample.sharding.kafka

import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.cluster.sharding.dynamic.DynamicShardAllocationStrategy
import akka.cluster.sharding.typed.ClusterShardingSettings
import akka.cluster.sharding.typed.ShardingMessageExtractor
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.sharding.typed.scaladsl.Entity
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey

object UserEvents {

  val TypeKey: EntityTypeKey[UserEvents.Message] = EntityTypeKey[UserEvents.Message]("user-processing")

  sealed trait Message {
    def userId: Long
  }
  sealed trait UserEvent extends Message
  case class UserAction(userId: Long, description: String, replyTo: ActorRef[Done]) extends UserEvent
  case class UserPurchase(userId: Long, product: String, quantity: Int, priceInPence: Int) extends UserEvent

  sealed trait UserQuery extends Message
  case class GetRunningTotal(userId: Long, replyTo: ActorRef[RunningTotal]) extends UserQuery

  case class RunningTotal(totalPurchases: Long, amountSpent: Long)

  def apply(userId: String): Behavior[Message] = running(RunningTotal(0, 0))

  private def running(runningTotal: RunningTotal): Behavior[Message] = {
    Behaviors.setup { ctx =>
      Behaviors.receiveMessage[Message] {
        case UserAction(_, desc, ack) =>
          ctx.log.info("user event {} from " + ack, desc)
          ack.tell(Done)
          Behaviors.same
        case UserPurchase(_, product, quantity, price) =>
          running(
            runningTotal.copy(
              totalPurchases = runningTotal.totalPurchases + 1,
              amountSpent = runningTotal.amountSpent + (quantity * price)))
      }
    }
  }

  class UserIdMessageExtractor extends ShardingMessageExtractor[Message, Message] {
    override def entityId(message: Message): String = message.userId.toString
    // the simplest scenario is that a kafka partition is the same as a cluster shard
    // in this example there are 128 partitions and 128 shards and they are always equal
    // otherwise this function must ensure that all messages for a shard come from the
    // same kafka partition
    // TODO add an example where partition != shard?
    override def shardId(entityId: String): String = math.abs(entityId.toLong % 128).toString
    override def unwrapMessage(message: Message): Message = message
  }

  def init(system: ActorSystem[_]): ActorRef[Message] = {
    ClusterSharding(system).init(
      Entity(TypeKey)(createBehavior = entityContext => UserEvents(entityContext.entityId))
        .withAllocationStrategy(() => new DynamicShardAllocationStrategy(system.toClassic, TypeKey.name))
        .withMessageExtractor(new UserIdMessageExtractor())
        .withSettings(ClusterShardingSettings(system)))
  }
}
