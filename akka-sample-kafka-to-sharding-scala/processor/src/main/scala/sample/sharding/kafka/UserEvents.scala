package sample.sharding.kafka

import akka.Done
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.cluster.sharding.external.ExternalShardAllocationStrategy
import akka.cluster.sharding.typed.ClusterShardingSettings
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}
import akka.kafka.{ConsumerSettings, KafkaShardingNoEnvelopeExtractor}
import org.apache.kafka.common.serialization.StringDeserializer

import scala.concurrent.duration._

object UserEvents {

  val TypeKey: EntityTypeKey[UserEvents.Message] =
    EntityTypeKey[UserEvents.Message]("user-processing")

  sealed trait Message extends CborSerializable {
    def userId: String
  }
  sealed trait UserEvent extends Message
  case class UserAction(userId: String, description: String, replyTo: ActorRef[Done]) extends UserEvent
  case class UserPurchase(userId: String, product: String, quantity: Long, priceInPence: Long, replyTo: ActorRef[Done])
      extends UserEvent

  sealed trait UserQuery extends Message
  case class GetRunningTotal(userId: String, replyTo: ActorRef[RunningTotal]) extends UserQuery

  case class RunningTotal(totalPurchases: Long, amountSpent: Long) extends CborSerializable

  def apply(): Behavior[Message] = running(RunningTotal(0, 0))

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
   * The KafkaShardingMessageExtractor uses the KafkaProducer's underlying DefaultPartitioner so that the same murmur2
   * hashing algorithm is used for Kafka and Akka Cluster Sharding
   */
  class UserIdMessageExtractor(clientSettings: ConsumerSettings[_,_], topic: String, groupId: String)
                              (implicit actorSystem: akka.actor.ActorSystem, timeout: FiniteDuration)
      extends KafkaShardingNoEnvelopeExtractor[Message](clientSettings, topic, groupId) {
    def entityId(message: Message): String = message.userId
  }

  def init(system: ActorSystem[_]): ActorRef[Message] = {
    val processorConfig = ProcessorConfig(system.settings.config.getConfig("kafka-to-sharding-processor"))
    implicit val classic: akka.actor.ActorSystem = system.toClassic
    implicit val timeout: FiniteDuration = 10.seconds
    val clientSettings = ConsumerSettings(classic, new StringDeserializer, new StringDeserializer)
    val topic = processorConfig.topics.head
    val groupId = processorConfig.groupId
    val messageExtractor = new UserIdMessageExtractor(clientSettings, topic, groupId)
    ClusterSharding(system).init(
      Entity(TypeKey)(createBehavior = _ => UserEvents())
        .withAllocationStrategy(new ExternalShardAllocationStrategy(system, TypeKey.name))
        .withMessageExtractor(messageExtractor)
        .withSettings(ClusterShardingSettings(system)))
  }

  def querySide(system: ActorSystem[_]): ActorRef[UserQuery] = {
    init(system).narrow[UserQuery]
  }
}
