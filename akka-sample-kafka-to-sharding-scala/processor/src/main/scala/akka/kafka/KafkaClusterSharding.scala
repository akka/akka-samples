package akka.kafka

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.{ActorSystem, ExtendedActorSystem}
import akka.cluster.sharding.typed.{ShardingEnvelope, ShardingMessageExtractor}
import akka.kafka.scaladsl.MetadataClient
import akka.util.Timeout._
import org.apache.kafka.common.utils.Utils

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.concurrent.duration._

object KafkaClusterSharding {
  private val metadataActorCounter = new AtomicInteger

  def messageExtractor[M](system: ActorSystem,
                          groupId: String,
                          topic: String,
                          timeout: FiniteDuration,
                          settings: ConsumerSettings[_,_]): Future[KafkaShardingMessageExtractor[M]] =
    getPartitionCount(system, topic, timeout, settings)
      .map(kafkaPartitions => new KafkaShardingMessageExtractor[M](groupId, kafkaPartitions))(system.dispatcher)

  def messageExtractorNoEnvelope[M](system: ActorSystem,
                                    groupId: String,
                                    topic: String,
                                    timeout: FiniteDuration,
                                    entityIdExtractor: M => String,
                                    settings: ConsumerSettings[_,_]): Future[KafkaShardingNoEnvelopeExtractor[M]] =
    getPartitionCount(system, topic, timeout, settings)
      .map(kafkaPartitions => new KafkaShardingNoEnvelopeExtractor[M](groupId, kafkaPartitions, entityIdExtractor))(system.dispatcher)

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
}

trait KafkaClusterSharding {
  def groupId: String
  def kafkaPartitions: Int

  def shardId(entityId: String): String = {
    // simplified version of Kafka's `DefaultPartitioner` implementation
    val partition = org.apache.kafka.common.utils.Utils.toPositive(Utils.murmur2(entityId.getBytes())) % kafkaPartitions
    s"$groupId-$partition"
  }
}

class KafkaShardingMessageExtractor[M](val groupId: String, val kafkaPartitions: Int)
  extends ShardingMessageExtractor[ShardingEnvelope[M], M] with KafkaClusterSharding {
  override def entityId(envelope: ShardingEnvelope[M]): String = envelope.entityId
  override def unwrapMessage(envelope: ShardingEnvelope[M]): M = envelope.message
}

/**
 * Caveats
 * - If Consumer subscription contains multiple topics, each topic has the exact same number of partitions.
 * - Values are passed as `null` to the partitioner.
 * - A fake [[org.apache.kafka.common.Cluster]] is passed to the [[org.apache.kafka.clients.producer.Partitioner]] that
 *   only contains partitions for the provided topic. If you choose to reuse a different partitioner then make sure your
 *   partitioner doesn't make use of any other Kafka Cluster metadata.
 */
class KafkaShardingNoEnvelopeExtractor[M](val groupId: String, val kafkaPartitions: Int, entityIdExtractor: M => String)
  extends ShardingMessageExtractor[M, M] with KafkaClusterSharding {
  override def entityId(message: M): String = entityIdExtractor(message)
  override def unwrapMessage(message: M): M = message
}
