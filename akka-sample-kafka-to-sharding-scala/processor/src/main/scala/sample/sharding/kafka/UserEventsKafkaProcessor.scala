package sample.sharding.kafka

import akka.Done
import akka.pattern.retry
import akka.actor.ActorSystem
import akka.actor.Scheduler
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.kafka.ConsumerSettings
import akka.kafka.Subscriptions
import akka.kafka.scaladsl.Consumer
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.Timeout
import akka.cluster.sharding.typed.delivery._
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import sample.sharding.kafka.serialization.UserPurchaseProto

import scala.concurrent.{ExecutionContextExecutor, Future, Promise}
import scala.concurrent.duration._
import scala.util.Try
import akka.cluster.sharding.typed.ShardingMessageExtractor
import akka.stream.stage.{GraphStage, GraphStageLogic, GraphStageLogicWithLogging, GraphStageWithMaterializedValue, InHandler, OutHandler}
import akka.stream.FlowShape
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.stream.Inlet
import akka.stream.Outlet
import akka.stream.Attributes
import akka.actor.ActorRef
import akka.cluster.sharding.typed.delivery.ShardingProducerController.RequestNext
import akka.stream.SinkShape
import akka.NotUsed
import akka.stream.scaladsl.Keep

import scala.util.control.NonFatal

object UserEventsKafkaProcessor {

  sealed trait Command

  private case class KafkaConsumerStopped(reason: Try[Any]) extends Command

  def apply(): Behavior[Nothing] = {
    Behaviors
      .setup[Command] { ctx =>
        val processorSettings = ProcessorConfig(ctx.system.settings.config.getConfig("kafka-to-sharding-processor"))
        implicit val classic: ActorSystem = ctx.system.toClassic
        implicit val ec: ExecutionContextExecutor = ctx.executionContext
        implicit val scheduler: Scheduler = classic.scheduler
        // TODO config
        val timeout = Timeout(3.seconds)
        val rebalancerRef = ctx.spawn(TopicListener(UserEvents.TypeKey), "rebalancerRef")

        // FIXME
        val shardRegion = UserEvents.shardingInit(ctx.system)

        val shardingProducerController = ctx.spawn(
          ShardingProducerController[UserEvents.Message]("producer-id", shardRegion, None),
          s"shardingController"
        )
        val consumerSettings =
          ConsumerSettings(ctx.system.toClassic, new StringDeserializer, new ByteArrayDeserializer)
            .withBootstrapServers(processorSettings.bootstrapServers)
            .withGroupId(processorSettings.groupId)
            .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            .withStopTimeout(0.seconds)

        val subscription = Subscriptions.topics(processorSettings.topic).withRebalanceListener(rebalancerRef.toClassic)

        val kafkaConsumer: Source[ConsumerRecord[String, Array[Byte]], Consumer.Control] =
          Consumer.plainSource(consumerSettings, subscription)

        val stream = kafkaConsumer
          .log("kafka-consumer")
          .filter(_.key() != null) // no entity id
          .map { record =>
            // alternatively the user id could be in the message rather than use the kafka key
            ctx.log.info(s"entityId->partition ${record.key()}->${record.partition()}")
            ctx.log.info("Forwarding message for entity {} to cluster sharding", record.key())
            val purchaseProto = UserPurchaseProto.parseFrom(record.value())
            ShardingEnvelope[UserEvents.Message](purchaseProto.userId,
              UserEvents.UserPurchase(
                purchaseProto.userId,
                purchaseProto.product,
                purchaseProto.quantity,
                purchaseProto.price
              ))
          }
          .toMat(ShardingToReliableDelivery.sink(shardingProducerController))(Keep.right)
          .run()

        stream.onComplete { result =>
          println("Stream finished " + result)
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

object ShardingToReliableDelivery {
  def sink[M](
               producerController: akka.actor.typed.ActorRef[ShardingProducerController.Command[M]]
             ): Sink[ShardingEnvelope[M], Future[Done]] = {
    Sink.fromGraph(new ShardingToReliableDeliveryStage(producerController))
  }
}

/**
 * Forwards messages to sharded entities via reliable delivery.
 *
 * Will keep requesting messages until there is a max number of messages buffered or
 * a single entity has too many buffered messages
 *
 * Should one slow entity be allowed to slow everything down? Option to start dropping for a given entity?
 */
class ShardingToReliableDeliveryStage[M](
                                          producerController: akka.actor.typed.ActorRef[ShardingProducerController.Command[M]]
                                        ) extends GraphStageWithMaterializedValue[SinkShape[ShardingEnvelope[M]], Future[Done]] {

  val in = Inlet[ShardingEnvelope[M]]("ShardingToReliableDelivery.in")

  val shape = SinkShape.of(in)

  val MaxBufferedMessages = 1000
  val MaxBufferedForSingleEntity = 100


  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[Done]) = {
    val promise = Promise[Done]()
    val logic = new GraphStageLogicWithLogging(shape) {
      var currentRequest: Option[RequestNext[M]] = None

      private def receive(message: (ActorRef, Any)): Unit = message match {
        case (_, rn: RequestNext[M]) =>
          try {
            currentRequest = Some(rn.asInstanceOf[RequestNext[M]])
            pullIfShardingDemand()
          } catch {
            case NonFatal(t) =>
              failStage(t)
              promise.tryFailure(t)
          }
        case msg =>
          log.warning("unexpected message to stage actor {}", msg)
      }

      override def preStart(): Unit = {
        import akka.actor.typed.scaladsl.adapter._
        val stageActor: ActorRef = getStageActor(receive).ref
        val asTyped = stageActor.toTyped[ShardingProducerController.RequestNext[M]]
        producerController ! ShardingProducerController.Start(asTyped)
        pull(in)
      }

      override def postStop(): Unit = {
        super.postStop()
        promise.trySuccess(Done)
      }

      /**
       * Enforces that max total buffered messages or max for a signle
       * entity.
       */
      private def reliableDeliveryFull(): Boolean = {
        currentRequest.exists(request => {
          val entityTooMany = request.bufferedForEntitiesWithoutDemand.find(_._2 >= MaxBufferedForSingleEntity)
          if (entityTooMany.isDefined) {
            log.info("Entity {} has max buffered messages. Not producing demand.", entityTooMany.get._1)
            true
          } else if (request.bufferedForEntitiesWithoutDemand.values.sum > MaxBufferedMessages) {
            log.info("Max number of messages in flight to reliable delivery")
            true
          } else {
            false
          }
        })
      }

      /**
       * Pulls a request has been received from reliable delivery, and that request
       * has not hit max buffers.
       */
      def pullIfShardingDemand(): Unit = {
        if (currentRequest.isDefined && !reliableDeliveryFull() && !hasBeenPulled(in)) {
          pull(in)
        }
      }

      setHandler(
        in,
        new InHandler {
          override def onUpstreamFinish(): Unit = {
            log.info("Upstream finished")
            super.onUpstreamFinish()
            promise.trySuccess(Done)
          }

          override def onUpstreamFailure(ex: Throwable): Unit = {
            log.error("Upstream failed", ex)
            super.onUpstreamFailure(ex)
            promise.tryFailure(ex)
          }

          override def onPush(): Unit = {
            log.info("onPush")
            // there should be demand from sharding and downstream, otherwise there would not have been a pull
            val next = grab(in)
            currentRequest match {
              case None => throw new IllegalStateException("onPush called when no demand from sharding")
              case Some(request) => request.sendNextTo ! next
            }
            pullIfShardingDemand()
          }
        }
      )
    }
    (logic, promise.future)
  }
}
