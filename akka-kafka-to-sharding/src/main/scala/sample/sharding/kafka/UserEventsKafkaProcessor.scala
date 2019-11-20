package sample.sharding.kafka

import akka.Done
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.kafka.ConsumerSettings
import akka.kafka.Subscriptions
import akka.kafka.scaladsl.Consumer
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.Timeout
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.IntegerDeserializer
import org.apache.kafka.common.serialization.StringDeserializer

import scala.concurrent.duration._

object UserEventsKafkaProcessor {

  // TODO settings
  private val groupId = "group-1"
  val KafkaBootstrapServers = "localhost:9092"
  val Topic = "user-events"

  def apply(): Behavior[Void] = { Behaviors.setup[Void] { ctx =>
    implicit val mat: Materializer = Materializer(ctx.system.toClassic)
    val timeout = Timeout(5.seconds)
    val rebalancerRef = ctx.spawn(TopicListener(UserEvents.TypeKey), "rebalancerRef")
    val shardRegion = UserEvents.init(ctx.system)
    val consumerSettings = ConsumerSettings(
      ctx.system.toClassic, new IntegerDeserializer, new StringDeserializer
    ).withBootstrapServers(KafkaBootstrapServers)
      .withGroupId(groupId)
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
      .withStopTimeout(0.seconds)

    // TODO use the non-actor version when next alpakka released
    val subscription = Subscriptions.topics(Topic)
      .withRebalanceListener(rebalancerRef.toClassic)

    val kafkaConsumer: Source[ConsumerRecord[Integer, String], Consumer.Control] =
      Consumer.plainSource(consumerSettings, subscription)

    // TODO deal with failures/restarts etc
      kafkaConsumer
        .mapAsync(100) { record =>
          // alternatively the user id could be in the message
          shardRegion.ask[Done](replyTo =>
              ShardingEnvelope(record.key().toString, UserEvents.UserAction(record.value(), replyTo))
          )(timeout, ctx.system.scheduler)
        }.runWith(Sink.ignore)
    Behaviors.empty[Void]
  }}

}
