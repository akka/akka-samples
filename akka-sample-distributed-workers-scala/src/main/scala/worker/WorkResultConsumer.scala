package worker

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator
import akka.actor.typed.scaladsl.adapter._

object WorkResultConsumer {
  def apply(): Behavior[Any] =
    Behaviors.setup[Any] { ctx =>
      val mediator = DistributedPubSub(ctx.system).mediator
      mediator ! DistributedPubSubMediator.Subscribe(WorkManager.ResultsTopic, ctx.self.toClassic)
      Behaviors.receiveMessage[Any] {
        case _: DistributedPubSubMediator.SubscribeAck =>
          ctx.log.info("Subscribed to {} topic", WorkManager.ResultsTopic)
          Behaviors.same
        case WorkResult(workId, result) =>
          ctx.log.info("Consumed result: {}", result)
          Behaviors.same
      }
    }

}
