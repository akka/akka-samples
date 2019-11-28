package sample.sharding.kafka

import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.cluster.sharding.ShardRegion.ShardId
import akka.cluster.sharding.dynamic.DynamicShardAllocationStrategy
import akka.cluster.sharding.typed.ClusterShardingSettings
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.ShardingMessageExtractor
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.sharding.typed.scaladsl.Entity
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.common.utils.Utils
import org.slf4j.LoggerFactory

object UserEvents {

  val TypeKey: EntityTypeKey[UserEvents.Message] = EntityTypeKey[UserEvents.Message]("user-processing")

  sealed trait Message {
    def userId: String
  }
  sealed trait UserEvent extends Message
  case class UserAction(userId: String, description: String, replyTo: ActorRef[Done]) extends UserEvent
  // TODO actually send this message so that running totals are updated, add serializer etc
  case class UserPurchase(userId: String, product: String, quantity: Int, priceInPence: Int) extends UserEvent

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
        case UserPurchase(_, product, quantity, price) =>
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

  /**
   * User the Kafka key as the entity id. Alternatively the entity id could be in the message
   * but then care must be taken that the all messages for the same entity id end up in the
   * same partition and that mapping is replicated in the [[shardId]] function.
   */
  class UserIdMessageExtractor(nrKafkaPartitions: Int) extends ShardingMessageExtractor[Message, Message] {

    private val log = LoggerFactory.getLogger(classOf[UserIdMessageExtractor])

    private val key = new StringSerializer

    override def entityId(message: Message): String = message.userId

    /**
     * The default partitioning strategy in Kafka is:
     * - If a partition is specified in the record, use it
     * - If no partition is specified but a key is present choose a partition based on a hash of the key
     *
     * If you have control of the producer side and can specify the entityId => partition mapping
     * explicitlyy, that is best. Otherwise below replicates the default partitioning strategy in
     * Kafka
     */
    override def shardId(entityId: String): ShardId = {
      // topic is not used in
      val keyBytes = key.serialize("not used", entityId)
      val shard = (Utils.toPositive(Utils.murmur2(keyBytes)) % nrKafkaPartitions).toString
      log.debug(s"entityId->shardId ${entityId}->${shard}")
      shard
    }

    override def unwrapMessage(message: Message): Message = message
  }

  def init(system: ActorSystem[_]): ActorRef[Message] = {
    val processorConfig = ProcessorConfig(system.settings.config.getConfig("kafka-to-sharding-processor"))
    ClusterSharding(system).init(
      Entity(TypeKey)(createBehavior = entityContext => UserEvents(entityContext.entityId))
        .withAllocationStrategy(() => new DynamicShardAllocationStrategy(system.toClassic, TypeKey.name))
        .withMessageExtractor(new UserIdMessageExtractor(processorConfig.nrPartitions))
        .withSettings(ClusterShardingSettings(system)))
  }

  def querySide(system: ActorSystem[_]): ActorRef[UserQuery] = {
    init(system).narrow[UserQuery]
  }
}
