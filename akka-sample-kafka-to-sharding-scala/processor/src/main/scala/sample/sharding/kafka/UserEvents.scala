package sample.sharding.kafka

import akka.Done
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.cluster.sharding.external.ExternalShardAllocationStrategy
import akka.cluster.sharding.typed.ClusterShardingSettings
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.kafka.cluster.sharding.KafkaClusterSharding

import scala.concurrent.Future
import scala.concurrent.duration._

object UserEvents {
  def init(system: ActorSystem[_], settings: ProcessorSettings): Future[ActorRef[Message]] = {
    import system.executionContext
    messageExtractor(settings).map(messageExtractor => {
      system.log.info("Message extractor created. Initializing sharding")
      ClusterSharding(system).init(
        Entity(settings.entityTypeKey)(createBehavior = _ => UserEvents())
          .withAllocationStrategy(new ExternalShardAllocationStrategy(system, settings.entityTypeKey.name))
          .withMessageExtractor(messageExtractor)
          .withSettings(ClusterShardingSettings(system)))
    })
  }

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
        case GetRunningTotal(id, replyTo) =>
          ctx.log.info("user {} running total queried", id)
          replyTo ! runningTotal
          Behaviors.same
      }
    }
  }

  private def messageExtractor(settings: ProcessorSettings): Future[KafkaClusterSharding.KafkaShardingNoEnvelopeExtractor[Message]] = {
    KafkaClusterSharding(settings.system).messageExtractorNoEnvelope(
      timeout = 10.seconds,
      topic = settings.topics.head,
      entityIdExtractor = (msg: Message) => msg.userId,
      settings = settings.kafkaConsumerSettings()
    )
  }
}
