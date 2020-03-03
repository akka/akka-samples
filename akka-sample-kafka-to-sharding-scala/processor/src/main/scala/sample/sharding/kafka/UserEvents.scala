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
import akka.actor.typed.delivery.ConsumerController
import akka.actor.typed.delivery.ConsumerController.Start
import akka.actor.typed.delivery.ConsumerController
import akka.cluster.sharding.typed.delivery.ShardingConsumerController
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.actor.typed.delivery.ConsumerController.SequencedMessage
import akka.cluster.sharding.typed.Murmur2MessageExtractor
import akka.actor.typed.delivery.ConsumerController.Confirmed

object UserEvents {

  val TypeKey: EntityTypeKey[SequencedMessage[UserEvents.Message]] =
    EntityTypeKey[SequencedMessage[UserEvents.Message]]("user-processing")

  sealed trait Message extends CborSerializable {
    def userId: String
  }
  sealed trait UserEvent extends Message
  case class UserAction(userId: String, description: String) extends UserEvent
  case class UserPurchase(userId: String, product: String, quantity: Long, priceInPence: Long)
      extends UserEvent

  sealed trait UserQuery extends Message
  case class GetRunningTotal(userId: String, replyTo: ActorRef[RunningTotal]) extends UserQuery

  final case class UserEventDelivery(message: Message, confirmTo: ActorRef[ConsumerController.Confirmed], seqNr: Long)

  case class RunningTotal(totalPurchases: Long, amountSpent: Long) extends CborSerializable

  def shardingInit(system: ActorSystem[_]): ActorRef[ShardingEnvelope[SequencedMessage[Message]]] = {
    val processorConfig = ProcessorConfig(system.settings.config.getConfig("kafka-to-sharding-processor"))
    val entity: Entity[SequencedMessage[Message], ShardingEnvelope[SequencedMessage[Message]]] = Entity[SequencedMessage[Message]](TypeKey)(_ => {
                                                   ShardingConsumerController(controller => UserEvents(controller))
                                                 })
    val entityWithExtractor = 
        entity
        .withAllocationStrategy(new ExternalShardAllocationStrategy(system, TypeKey.name))
          .withMessageExtractor(new Murmur2MessageExtractor[SequencedMessage[Message]](processorConfig.nrPartitions))
        .withSettings(ClusterShardingSettings(system))

    ClusterSharding(system).init(entityWithExtractor)
  }

  def apply(controller: ActorRef[ConsumerController.Start[Message]]): Behavior[UserEventDelivery] = {
    Behaviors.setup { ctx =>
      val messageAdapter: ActorRef[ConsumerController.Delivery[Message]] =
        ctx.messageAdapter(d => UserEventDelivery(d.msg, d.confirmTo, d.seqNr))
      controller ! Start(messageAdapter)
      running(RunningTotal(0, 0))
    }
  }

  private def running(runningTotal: RunningTotal): Behavior[UserEventDelivery] = {
    Behaviors.setup { ctx =>
      Behaviors.receiveMessage {
        case UserEventDelivery(msg, confirmTo, seqNr) =>
          msg match {
            case UserAction(_, desc) =>
              ctx.log.info("user event {}", desc)
              confirmTo ! Confirmed(seqNr)
              Behaviors.same
            case UserPurchase(id, product, quantity, price) =>
              ctx.log.info("user {} purchase {}, quantity {}, price {}", id, product, quantity, price)
              confirmTo ! Confirmed(seqNr)
              running(
                runningTotal.copy(
                  totalPurchases = runningTotal.totalPurchases + 1,
                  amountSpent = runningTotal.amountSpent + (quantity * price)
                )
              )
            case GetRunningTotal(_, replyTo) =>
              replyTo ! runningTotal
              confirmTo ! Confirmed(seqNr)
              Behaviors.same
          }
      }
    }
  }

  /*
   * The murmur2 message extractor matches kafka's default partitioning when messages
   * have keys that are strings
   */
  class UserIdMessageExtractor(nrKafkaPartitions: Int)
      extends Murmur2NoEnvelopeMessageExtractor[SequencedMessage[Message]](nrKafkaPartitions) {
    override def entityId(message: SequencedMessage[Message]): String = message.msg.userId
  }

   /* 
  def init(system: ActorSystem[_]): ActorRef[Message] = {
    val processorConfig = ProcessorConfig(system.settings.config.getConfig("kafka-to-sharding-processor"))
    ClusterSharding(system).init(
      Entity(TypeKey)(createBehavior = _ => UserEvents())
        .withAllocationStrategy(new ExternalShardAllocationStrategy(system, TypeKey.name))
        .withMessageExtractor(new UserIdMessageExtractor(processorConfig.nrPartitions))
        .withSettings(ClusterShardingSettings(system))
    )
  }
    */

}
