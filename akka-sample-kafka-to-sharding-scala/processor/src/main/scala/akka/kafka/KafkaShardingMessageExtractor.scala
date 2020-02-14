package akka.kafka

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.{ActorSystem, ExtendedActorSystem}
import akka.cluster.sharding.typed.{ShardingEnvelope, ShardingMessageExtractor}
import akka.kafka.DefaultKafkaShardingMessageExtractor.PartitionCountStrategy
import akka.kafka.scaladsl.MetadataClient
import akka.util.Timeout._
import org.apache.kafka.common.utils.Utils

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

object DefaultKafkaShardingMessageExtractor {
  sealed trait PartitionCountStrategy {
    def groupId: String
    def partitions: Int
  }
  final case class Provided(groupId: String, partitions: Int) extends PartitionCountStrategy
  final case class RetrieveFromKafka(
                                      system: ActorSystem,
                                      timeout: FiniteDuration,
                                      groupId: String,
                                      topic: String,
                                      settings: ConsumerSettings[_,_])
                                    extends PartitionCountStrategy {
    import RetrieveFromKafka._
    private implicit val ec: ExecutionContext = system.dispatcher
    lazy val partitions: Int = {
      val actorNum = metadataActorCounter.getAndIncrement()
      val consumerActor = system
        .asInstanceOf[ExtendedActorSystem]
        .systemActorOf(KafkaConsumerActor.props(settings), s"metadata-consumer-actor-$actorNum")
      val metadataClient = MetadataClient.create(consumerActor, timeout)
      val numPartitions = metadataClient.getPartitionsFor(topic).map(_.length)
      numPartitions.foreach(num => system.log.info("Retrieved {} partitions for topic '{}' for group '{}'", num, topic, groupId))
      Await.result(numPartitions, timeout)
    }
  }
  object RetrieveFromKafka {
    private val metadataActorCounter = new AtomicInteger
  }
}

private[kafka] trait DefaultKafkaShardingMessageExtractor {
  val strategy: PartitionCountStrategy
  private val groupId: String = strategy.groupId
  private val kafkaPartitions: Int = strategy.partitions

  def shardId(entityId: String): String = {
    // simplified version of Kafka's `DefaultPartitioner` implementation
    val partition = org.apache.kafka.common.utils.Utils.toPositive(Utils.murmur2(entityId.getBytes())) % kafkaPartitions
    s"$groupId-$partition"
  }
}

final class KafkaShardingMessageExtractor[M](val strategy: PartitionCountStrategy)
  extends ShardingMessageExtractor[ShardingEnvelope[M], M] with DefaultKafkaShardingMessageExtractor {
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
abstract class KafkaShardingNoEnvelopeExtractor[M](val strategy: PartitionCountStrategy)
  extends ShardingMessageExtractor[M, M] with DefaultKafkaShardingMessageExtractor {
  override def unwrapMessage(message: M): M = message
}
