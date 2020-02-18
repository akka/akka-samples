package akka.kafka

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.{ActorSystem, ExtendedActorSystem}
import akka.cluster.sharding.external.ExternalShardAllocation
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.cluster.sharding.typed.{ShardingEnvelope, ShardingMessageExtractor}
import akka.cluster.typed.Cluster
import akka.kafka.scaladsl.MetadataClient
import akka.util.Timeout._
import org.apache.kafka.common.utils.Utils

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}

/**
 * Utilities to enable Akka Cluster External Sharding with Alpakka Kafka.
 */
object KafkaClusterSharding {
  private val metadataActorCounter = new AtomicInteger

  /**
   * Asynchronously return a [[ShardingMessageExtractor]] with a default hashing strategy based on Apache Kafka's
   * [[org.apache.kafka.clients.producer.internals.DefaultPartitioner]].
   *
   * The number of partitions to use with the hashing strategy will be automatically determined by querying the Kafka
   * cluster for the number of partitions of a user provided [[topic]]. Use the [[settings]] parameter to configure
   * the Kafka Consumer connection required to retrieve the number of partitions.
   *
   * _Important_: All topics used in a Consumer [[Subscription]] must contain the same number of partitions to ensure
   * that entities are routed to the same Entity type.
   */
  def messageExtractor[M](system: ActorSystem,
                          topic: String,
                          timeout: FiniteDuration,
                          settings: ConsumerSettings[_,_]): Future[KafkaShardingMessageExtractor[M]] =
    getPartitionCount(system, topic, timeout, settings)
      .map(kafkaPartitions => new KafkaShardingMessageExtractor[M](kafkaPartitions))(system.dispatcher)

  /**
   * Asynchronously return a [[ShardingMessageExtractor]] with a default hashing strategy based on Apache Kafka's
   * [[org.apache.kafka.clients.producer.internals.DefaultPartitioner]].
   *
   * The number of partitions to use with the hashing strategy will be automatically determined by querying the Kafka
   * cluster for the number of partitions of a user provided [[topic]]. Use the [[settings]] parameter to configure
   * the Kafka Consumer connection required to retrieve the number of partitions. Use the [[entityIdExtractor]] to pick
   * a field from the Entity to use as the entity id for the hashing strategy.
   *
   * _Important_: All topics used in a Consumer [[Subscription]] must contain the same number of partitions to ensure
   * that entities are routed to the same Entity type.
   */
  def messageExtractorNoEnvelope[M](system: ActorSystem,
                                    topic: String,
                                    timeout: FiniteDuration,
                                    entityIdExtractor: M => String,
                                    settings: ConsumerSettings[_,_]): Future[KafkaShardingNoEnvelopeExtractor[M]] =
    getPartitionCount(system, topic, timeout, settings)
      .map(kafkaPartitions => new KafkaShardingNoEnvelopeExtractor[M](kafkaPartitions, entityIdExtractor))(system.dispatcher)

  private def getPartitionCount[M](system: ActorSystem, topic: String, timeout: FiniteDuration, settings: ConsumerSettings[_, _]): Future[Int] = {
    implicit val ec: ExecutionContextExecutor = system.dispatcher
    val actorNum = metadataActorCounter.getAndIncrement()
    val consumerActor = system
      .asInstanceOf[ExtendedActorSystem]
      .systemActorOf(KafkaConsumerActor.props(settings), s"metadata-consumer-actor-$actorNum")
    val metadataClient = MetadataClient.create(consumerActor, timeout)
    val numPartitions = metadataClient.getPartitionsFor(topic).map(_.length)
    numPartitions.map { count =>
      system.log.info("Retrieved {} partitions for topic '{}'", count, topic)
      count
    }
  }

  sealed trait KafkaClusterSharding {
    def kafkaPartitions: Int
    def shardId(entityId: String): String = {
      // simplified version of Kafka's `DefaultPartitioner` implementation
      val partition = org.apache.kafka.common.utils.Utils.toPositive(Utils.murmur2(entityId.getBytes())) % kafkaPartitions
      partition.toString
    }
  }

  final class KafkaShardingMessageExtractor[M](val kafkaPartitions: Int)
    extends ShardingMessageExtractor[ShardingEnvelope[M], M] with KafkaClusterSharding {
    override def entityId(envelope: ShardingEnvelope[M]): String = envelope.entityId
    override def unwrapMessage(envelope: ShardingEnvelope[M]): M = envelope.message
  }

  final class KafkaShardingNoEnvelopeExtractor[M](val kafkaPartitions: Int, entityIdExtractor: M => String)
    extends ShardingMessageExtractor[M, M] with KafkaClusterSharding {
    override def entityId(message: M): String = entityIdExtractor(message)
    override def unwrapMessage(message: M): M = message
  }

  // TODO:
  // - will require `akka-actors-typed` as another provided dep, or should we just return a classic actor?
  // - returning a typed actor is more flexible for the user so that they can easy create it under the `user` guardian
  //   when running akka typed. an alternative would be to create the actor ourself as a system actor, like is done with
  //   the KafkaConsumerActor for the metadata client.
  /**
   * The [[RebalanceListener]] handles [[TopicPartitionsAssigned]] events created by the Kafka consumer group rebalance
   * listener. As partitions are assigned to this consumer group member we update the External Sharding strategy
   * accordingly so that entities are (eventually) routed to the local Akka cluster member.
   */
  object RebalanceListener {
    def apply(typeKey: EntityTypeKey[_]): Behavior[ConsumerRebalanceEvent] =
      Behaviors.setup { ctx =>
        val typeKeyName = typeKey.name
        val shardAllocationClient = ExternalShardAllocation(ctx.system).clientFor(typeKeyName)
        val address = Cluster(ctx.system).selfMember.address
        Behaviors.receive[ConsumerRebalanceEvent] {
          case (ctx, TopicPartitionsAssigned(_, partitions)) =>
            import ctx.executionContext
            val partitionsList = partitions.mkString(",")
            ctx.log.info("Consumer group '{}' is assigning topic partitions to cluster member '{}': [{}]",
              typeKeyName, address, partitionsList)
            val updates = partitions.map { tp =>
              val shardId = tp.partition().toString
              // Kafka partition number becomes the akka shard id
              // TODO: support assigning more than 1 shard id at once?
              shardAllocationClient.updateShardLocation(shardId, address)
            }
            // TODO: pipeToSelf since we're closing over local state?
            Future
              .sequence(updates)
              // each Future returns successfully once a majority of cluster nodes receive the update.
              // there's no point blocking here because the rebalance listener is triggered asynchronously.  if we want
              // to block rebalances then we should provide an implementing using the `PartitionAssignmentHandler` instead
              .onComplete {
                case Success(_) =>
                  ctx.log.info("Completed consumer group '{}' assignment of topic partitions to cluster member '{}': [{}]",
                    typeKeyName, address, partitionsList)
                case Failure(ex) =>
                  ctx.log.error("A failure occurred while updating cluster shards", ex)
              }
            Behaviors.same
          case (_, TopicPartitionsRevoked(_, _)) => Behaviors.same
        }
      }
  }
}