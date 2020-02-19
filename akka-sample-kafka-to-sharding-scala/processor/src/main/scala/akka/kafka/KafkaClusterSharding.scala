package akka.kafka

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.{ActorRef, ActorSystem, ExtendedActorSystem}
import akka.annotation.{ApiMayChange, InternalApi}
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
   * API MAY CHANGE
   *
   * Asynchronously return a [[ShardingMessageExtractor]] with a default hashing strategy based on Apache Kafka's
   * [[org.apache.kafka.clients.producer.internals.DefaultPartitioner]].
   *
   * The number of partitions to use with the hashing strategy will be automatically determined by querying the Kafka
   * cluster for the number of partitions of a user provided [[topic]]. Use the [[settings]] parameter to configure
   * the Kafka Consumer connection required to retrieve the number of partitions.
   *
   * All topics used in a Consumer [[Subscription]] must contain the same number of partitions to ensure
   * that entities are routed to the same Entity type.
   */
  @ApiMayChange
  def messageExtractor[M](system: ActorSystem,
                          topic: String,
                          timeout: FiniteDuration,
                          settings: ConsumerSettings[_,_]): Future[KafkaShardingMessageExtractor[M]] =
    getPartitionCount(system, topic, timeout, settings)
      .map(kafkaPartitions => new KafkaShardingMessageExtractor[M](kafkaPartitions))(system.dispatcher)

  /**
   * API MAY CHANGE
   *
   * Asynchronously return a [[ShardingMessageExtractor]] with a default hashing strategy based on Apache Kafka's
   * [[org.apache.kafka.clients.producer.internals.DefaultPartitioner]].
   *
   * The number of partitions to use with the hashing strategy will be automatically determined by querying the Kafka
   * cluster for the number of partitions of a user provided [[topic]]. Use the [[settings]] parameter to configure
   * the Kafka Consumer connection required to retrieve the number of partitions. Use the [[entityIdExtractor]] to pick
   * a field from the Entity to use as the entity id for the hashing strategy.
   *
   * All topics used in a Consumer [[Subscription]] must contain the same number of partitions to ensure
   * that entities are routed to the same Entity type.
   */
  @ApiMayChange
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

  @InternalApi
  sealed trait KafkaClusterSharding {
    def kafkaPartitions: Int
    def shardId(entityId: String): String = {
      // simplified version of Kafka's `DefaultPartitioner` implementation
      val partition = org.apache.kafka.common.utils.Utils.toPositive(Utils.murmur2(entityId.getBytes())) % kafkaPartitions
      partition.toString
    }
  }

  @InternalApi
  final class KafkaShardingMessageExtractor[M] private[kafka](val kafkaPartitions: Int)
    extends ShardingMessageExtractor[ShardingEnvelope[M], M] with KafkaClusterSharding {
    override def entityId(envelope: ShardingEnvelope[M]): String = envelope.entityId
    override def unwrapMessage(envelope: ShardingEnvelope[M]): M = envelope.message
  }

  @InternalApi
  final class KafkaShardingNoEnvelopeExtractor[M] private[kafka](val kafkaPartitions: Int, entityIdExtractor: M => String)
    extends ShardingMessageExtractor[M, M] with KafkaClusterSharding {
    override def entityId(message: M): String = entityIdExtractor(message)
    override def unwrapMessage(message: M): M = message
  }

  // TODO: will require `akka-actors-typed` as a provided dep
  /**
   * API MAY CHANGE
   *
   * Create an Alpakka Kafka rebalance listener that handles [[TopicPartitionsAssigned]] events. The [[typeKey]] is used
   * to create the [[ExternalShardAllocation]] client. When partitions are assigned to this consumer group member the
   * rebalance listener will use the [[ExternalShardAllocation]] client to update the External Sharding strategy
   * accordingly so that entities are (eventually) routed to the local Akka cluster member.
   *
   * Returns an Akka classic [[ActorRef]] that can be passed to an Alpakka Kafka [[ConsumerSettings]].
   */
  @ApiMayChange
  def rebalanceListener(system: ActorSystem, typeKey: EntityTypeKey[_]): ActorRef = {
    val actor: Behavior[ConsumerRebalanceEvent] = Behaviors.setup { ctx =>
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
          Future
            .sequence(updates)
            // Each Future returns successfully once a majority of cluster nodes receive the update. There's no point
            // blocking here because the rebalance listener is triggered asynchronously. If we want to block during
            // rebalance then we should provide an implementation using the `PartitionAssignmentHandler` instead
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

    system
      .toTyped
      .systemActorOf(actor, "kafka-cluster-sharding-rebalance-listener")
      .toClassic
  }
}