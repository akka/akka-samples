package sample.sharding.kafka

import akka.Done
import akka.pattern.retry
import akka.actor.ActorSystem
import akka.actor.Scheduler
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.cluster.sharding.typed.ShardingMessageExtractor
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.kafka.{CommitterSettings, ConsumerMessage, ConsumerSettings, KafkaClusterSharding, Subscriptions}
import akka.kafka.scaladsl.{Committer, Consumer}
import akka.stream.scaladsl.SourceWithContext
import akka.util.Timeout
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import sample.sharding.kafka.serialization.UserPurchaseProto

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

object UserEventsKafkaProcessor {

  sealed trait Command
  private case class KafkaConsumerStopped(reason: Try[Any]) extends Command

  def apply(extractor: ShardingMessageExtractor[UserEvents.Message, UserEvents.Message]): Behavior[Nothing] = {
    Behaviors
      .setup[Command] { ctx =>
        val processorSettings = ProcessorConfig(ctx.system.settings.config.getConfig("kafka-to-sharding-processor"))
        implicit val classic: ActorSystem = ctx.system.toClassic
        implicit val ec: ExecutionContextExecutor = ctx.executionContext
        implicit val scheduler: Scheduler = classic.scheduler
        // TODO config
        val timeout = Timeout(3.seconds)
        val typeKey = EntityTypeKey[UserEvents.Message](processorSettings.groupId)
        val rebalanceListener = KafkaClusterSharding.rebalanceListener(classic, typeKey)
        val shardRegion = UserEvents.init(ctx.system, extractor, processorSettings.groupId)
        val consumerSettings =
          ConsumerSettings(classic, new StringDeserializer, new ByteArrayDeserializer)
            .withBootstrapServers(processorSettings.bootstrapServers)
            .withGroupId(processorSettings.groupId)
            .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            .withStopTimeout(0.seconds)

        val subscription = Subscriptions
          .topics(processorSettings.topics: _*)
          .withRebalanceListener(rebalanceListener)

        val kafkaConsumer: SourceWithContext[ConsumerRecord[String, Array[Byte]], ConsumerMessage.CommittableOffset, Consumer.Control] =
          Consumer.sourceWithOffsetContext(consumerSettings, subscription)

        val stream: Future[Done] = kafkaConsumer
          .log("kafka-consumer")
          .filter(_.key() != null) // no entity id
          .mapAsync(20) { record =>
            // alternatively the user id could be in the message rather than use the kafka key
            ctx.log.info(s"entityId->partition ${record.key()}->${record.partition()}")
            ctx.log.info("Forwarding message for entity {} to cluster sharding", record.key())
            // TODO idempotency? reliable delivery (once released)?
            retry(
              () =>
                shardRegion.ask[Done](replyTo => {
                  val purchaseProto = UserPurchaseProto.parseFrom(record.value())
                  UserEvents.UserPurchase(
                    purchaseProto.userId,
                    purchaseProto.product,
                    purchaseProto.quantity,
                    purchaseProto.price,
                    replyTo)
                })(timeout, ctx.system.scheduler),
              attempts = 3,
              delay = 1.second
            )
          }
          .runWith(Committer.sinkWithOffsetContext(CommitterSettings(classic)))

        stream.onComplete { result =>
          ctx.self ! KafkaConsumerStopped(result)
        }
        Behaviors.receiveMessage[Command] {
          case KafkaConsumerStopped(reason) =>
            ctx.log.info("Consumer stopped {}", reason)
            Behaviors.stopped
        }
      }
      .narrow
  }

}
