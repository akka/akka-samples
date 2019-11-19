package sample.sharding.kafka

import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.cluster.sharding.dynamic.DynamicShardAllocation
import akka.cluster.sharding.dynamic.DynamicShardAllocationStrategy
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.sharding.typed.scaladsl.Entity
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.cluster.typed.Cluster
import akka.kafka.ConsumerRebalanceEvent
import akka.kafka.ConsumerSettings
import akka.kafka.Subscriptions
import akka.kafka.TopicPartitionsAssigned
import akka.kafka.TopicPartitionsRevoked
import akka.kafka.scaladsl.Consumer
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.IntegerDeserializer
import org.apache.kafka.common.serialization.StringDeserializer

import scala.concurrent.duration._

object UserEvents {
  sealed trait Command
  case class UserAction(description: String, replyTo: ActorRef[Done])
      extends Command
  def apply(userId: String): Behavior[Command] = Behaviors.setup { ctx =>
    Behaviors.receiveMessage[Command] {
      case UserAction(desc, ack) =>
        ctx.log.info("user event {}", desc)
        ack.tell(Done)
        Behaviors.same
    }
  }
}

object Main extends App {

  val TypeName = "user-processing"
  val TypeKey = EntityTypeKey[UserEvents.Command](TypeName)

  val groupId = "group-1"
  val kafkaBootstrapServers = "localhost:9092"

  val topicListener: Behavior[ConsumerRebalanceEvent] = Behaviors.setup { ctx =>
    val shardAllocationClient = DynamicShardAllocation(ctx.system.toClassic)
      .clientFor(TypeName)
    val address = Cluster(ctx.system).selfMember.address
    Behaviors.receiveMessage[ConsumerRebalanceEvent] {
      case TopicPartitionsAssigned(_, partitions) =>
        partitions.foreach(partition => {
          ctx.log.info(
            "Partition [{}] assigned to current node. Updating shard allocation",
            partition.partition()
          )
          // TODO deal with failure? just retry or pipe to self and do something?
          shardAllocationClient
            .updateShardLocation(partition.partition().toString, address)
        })
        Behaviors.same
      case TopicPartitionsRevoked(_, topicPartitions) =>
        ctx.log.info(
          "Partitions [{}] revoked from current node. New location will update shard allocation",
          topicPartitions.mkString(",")
        )
        Behaviors.same
    }
  }

  val actorSystem =
    ActorSystem(Behaviors.setup[Void] {
      ctx =>
        // TODO use typed stream
        implicit val mat = Materializer(ctx.system.toClassic)
        val rebalancerRef = ctx.spawn(topicListener, "rebalancerRef")

        val sharding = ClusterSharding(ctx.system)

        val shardRegion: ActorRef[ShardingEnvelope[UserEvents.Command]] =
          sharding.init(
            Entity(TypeKey)(
              createBehavior = entityContext =>
                UserEvents(entityContext.entityId)
            ).withAllocationStrategy(new DynamicShardAllocationStrategy(ctx.system.toClassic, TypeKey.name))
          )
        val consumerSettings = ConsumerSettings(
          ctx.system.toClassic,
          new IntegerDeserializer,
          new StringDeserializer
        ).withBootstrapServers(kafkaBootstrapServers)
          .withGroupId(groupId)
          .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
          .withStopTimeout(0.seconds)

        val subscription = Subscriptions
          .topics("send-to-sharding")
          .withRebalanceListener(rebalancerRef.toClassic)

        val kafkaConsumer
          : Source[ConsumerRecord[Integer, String], Consumer.Control] =
          Consumer.plainSource(consumerSettings, subscription)

        import akka.actor.typed.scaladsl.AskPattern._
        import akka.util.Timeout
        val timeout = Timeout(5.seconds)
        val sendToSharding: Source[Done, Consumer.Control] =
          kafkaConsumer
            .mapAsync(100) { record =>
              ctx.log.info("Kafka message {}", record)
              // alternatively the user id could be in the message and we can decide get the entity id having parsed the message
              shardRegion.ask[Done](
                replyTo =>
                  ShardingEnvelope(
                    record.key().toString,
                    UserEvents.UserAction(record.value(), replyTo)
                )
              )(timeout, ctx.system.scheduler)
            }

        sendToSharding.runWith(Sink.ignore)

        // TODO commiting?

        Behaviors.empty[Void]
    }, "KafkaToSharding")

}
