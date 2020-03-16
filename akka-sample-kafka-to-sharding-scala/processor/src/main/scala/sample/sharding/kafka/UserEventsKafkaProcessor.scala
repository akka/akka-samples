package sample.sharding.kafka

import akka.Done
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.{ActorSystem, Scheduler}
import akka.kafka.cluster.sharding.KafkaClusterSharding
import akka.kafka.scaladsl.{Committer, Consumer}
import akka.kafka.{CommitterSettings, Subscriptions}
import akka.pattern.retry
import sample.sharding.kafka.serialization.UserPurchaseProto

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.Try

object UserEventsKafkaProcessor {

  sealed trait Command

  private case class KafkaConsumerStopped(reason: Try[Any]) extends Command

  def apply(shardRegion: ActorRef[UserEvents.Command], processorSettings: ProcessorSettings): Behavior[Nothing] = {
    Behaviors
      .setup[Command] { ctx =>
        implicit val classic: ActorSystem = ctx.system.toClassic
        implicit val ec: ExecutionContextExecutor = ctx.executionContext
        implicit val scheduler: Scheduler = classic.scheduler

        val rebalanceListener = KafkaClusterSharding(classic).rebalanceListener(processorSettings.entityTypeKey)

        val subscription = Subscriptions
          .topics(processorSettings.topics: _*)
          .withRebalanceListener(rebalanceListener.toClassic)

        val stream: Future[Done] = Consumer.sourceWithOffsetContext(processorSettings.kafkaConsumerSettings(), subscription)
          // MapAsync and Retries can be replaced by reliable delivery
          .mapAsync(20) { record =>
            ctx.log.info(s"user id consumed kafka partition ${record.key()}->${record.partition()}")
            retry(() =>
              shardRegion.ask[Done](replyTo => {
                val purchaseProto = UserPurchaseProto.parseFrom(record.value())
                UserEvents.UserPurchase(
                  purchaseProto.userId,
                  purchaseProto.product,
                  purchaseProto.quantity,
                  purchaseProto.price,
                  replyTo)
              })(processorSettings.askTimeout, ctx.system.scheduler),
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
