package sample.sharding.kafka

import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.external.ExternalShardAllocationStrategy
import akka.cluster.sharding.typed.ClusterShardingSettings
import akka.cluster.sharding.typed.Murmur2NoEnvelopeMessageExtractor
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.sharding.typed.scaladsl.Entity
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey

object UserEvents {

  val TypeKey: EntityTypeKey[UserEvents.Message] =
    EntityTypeKey[UserEvents.Message]("user-processing")

  sealed trait Message {
    def userId: String
  }
  sealed trait UserEvent extends Message with CborSerializable
  case class UserAction(userId: String, description: String, replyTo: ActorRef[Done]) extends UserEvent
  case class UserPurchase(userId: String, product: String, quantity: Long, priceInPence: Long, replyTo: ActorRef[Done])
      extends UserEvent

  sealed trait UserQuery extends Message
  case class GetRunningTotal(userId: String, replyTo: ActorRef[RunningTotal]) extends UserQuery

  case class RunningTotal(totalPurchases: Long, amountSpent: Long)

  def apply(userId: String): Behavior[Message] = running(RunningTotal(0, 0))

  private def running(runningTotal: RunningTotal): Behavior[Message] = {
    Behaviors.setup { ctx =>
      Behaviors.receiveMessage[Message] {
        case UserAction(_, desc, ack) =>
          ctx.log.info("user event {}", desc)
          ack.tell(Done)
          Behaviors.same
        case UserPurchase(id, product, quantity, price, ack) =>
          ctx.log.info("user {} purchase {}, quantity {}, price {}", id, product, quantity, price)
          ack.tell(Done)
          running(
            runningTotal.copy(
              totalPurchases = runningTotal.totalPurchases + 1,
              amountSpent = runningTotal.amountSpent + (quantity * price)))
        case GetRunningTotal(_, replyTo) =>
          replyTo ! runningTotal
          Behaviors.same
      }
    }
  }

  /*
   * The murmur2 message extractor matches kafka's default partitioning when messages
   * have keys that are strings
   */
  class UserIdMessageExtractor(nrKafkaPartitions: Int)
      extends Murmur2NoEnvelopeMessageExtractor[Message](nrKafkaPartitions) {
    override def entityId(message: Message): String = message.userId
  }

  def init(system: ActorSystem[_]): ActorRef[Message] = {
    val processorConfig = ProcessorConfig(system.settings.config.getConfig("kafka-to-sharding-processor"))
    ClusterSharding(system).init(
      Entity(TypeKey)(createBehavior = entityContext => UserEvents(entityContext.entityId))
        .withAllocationStrategy(new ExternalShardAllocationStrategy(system, TypeKey.name))
        .withMessageExtractor(new UserIdMessageExtractor(processorConfig.nrPartitions))
        .withSettings(ClusterShardingSettings(system)))
  }

  def querySide(system: ActorSystem[_]): ActorRef[UserQuery] = {
    init(system).narrow[UserQuery]
  }
}
