package worker

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator
import akka.actor.typed.scaladsl.adapter._

// #work-result-consumer
object WorkResultConsumer {
  def apply(): Behavior[Any] =
    Behaviors
      .setup[Any] { ctx =>
        val mediator = DistributedPubSub(ctx.system.toClassic).mediator
        mediator ! DistributedPubSubMediator.Subscribe(
          Master.ResultsTopic,
          ctx.self.toClassic
        )
        Behaviors.receiveMessage[Any] {
          case _: DistributedPubSubMediator.SubscribeAck =>
            ctx.log.info("Subscribed to {} topic", Master.ResultsTopic)
            Behaviors.same
          case WorkResult(workId, result) =>
            ctx.log.info("Consumed result: {}", result)
            Behaviors.same
        }
      }

}
// #work-result-consumer
