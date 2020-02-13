package akka.kafka

import akka.actor.{ActorSystem, ExtendedActorSystem}
import akka.cluster.sharding.typed.{ShardingEnvelope, ShardingMessageExtractor}
import akka.kafka.scaladsl.MetadataClient
import akka.util.Timeout._
import org.apache.kafka.clients.producer.Partitioner
import org.apache.kafka.clients.producer.internals.DefaultPartitioner
import org.apache.kafka.common.{Node, PartitionInfo, Cluster => KafkaCluster}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.jdk.CollectionConverters._

private[kafka] trait DefaultKafkaShardingMessageExtractor {
  implicit val actorSystem: ActorSystem
  implicit val timeout: FiniteDuration
  implicit val ec: ExecutionContextExecutor = actorSystem.dispatcher

  val clientSettings: ConsumerSettings[_, _]
  val groupId: String
  val topic: String

  private val CLUSTER_ID = "cluster-id"
  private val kafkaPartitioner = partitioner()
  private val kafkaCluster = cluster(partitions())

  def shardId(entityId: String): String = {
    val partition = kafkaPartitioner
      .partition(topic, entityId, entityId.getBytes(), null, null, kafkaCluster)
    s"$groupId-$partition"
  }

  def partitions(): List[PartitionInfo] = {
    val consumerActor = actorSystem.actorOf(KafkaConsumerActor.props(clientSettings), "metadata-consumer-actor")
    val metadataClient = MetadataClient.create(consumerActor, timeout)
    val partitions = metadataClient.getPartitionsFor(topic)
    partitions.foreach(p => actorSystem.log.info("Retrieved %s partitions for topic %s for group %s", p.length, topic, groupId))
    Await.result(partitions, timeout)
  }

  def cluster(partitions: List[PartitionInfo]): KafkaCluster =
    new KafkaCluster(CLUSTER_ID, List.empty[Node].asJavaCollection, partitions.asJavaCollection, Set.empty[String].asJava, Set.empty[String].asJava)

  def partitioner(): Partitioner = new DefaultPartitioner()
}

final class KafkaShardingMessageExtractor[M](val clientSettings: ConsumerSettings[_,_], val groupId: String, val topic: String)
                                            (implicit val actorSystem: ActorSystem, val timeout: FiniteDuration)
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
abstract class KafkaShardingNoEnvelopeExtractor[M](val clientSettings: ConsumerSettings[_,_], val groupId: String, val topic: String)
                                                  (implicit val actorSystem: ActorSystem, val timeout: FiniteDuration)
  extends ShardingMessageExtractor[M, M] with DefaultKafkaShardingMessageExtractor {
  override def unwrapMessage(message: M): M = message
}
