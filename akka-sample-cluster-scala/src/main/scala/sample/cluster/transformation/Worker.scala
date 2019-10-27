package sample.cluster.transformation

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.Behaviors

//#worker
object Worker {

  val WorkerServiceKey = ServiceKey[Worker.TransformText]("Worker")

  sealed trait Command
  final case class TransformText(text: String, replyTo: ActorRef[TextTransformed]) extends Command
  final case class TextTransformed(text: String)

  def apply(): Behavior[Command] =
    Behaviors.setup { ctx =>
      // each worker registers themselves with the receptionist
      ctx.system.receptionist ! Receptionist.Register(WorkerServiceKey, ctx.self)

      Behaviors.receiveMessage {
        case TransformText(text, replyTo) =>
          replyTo ! TextTransformed(text.toUpperCase)
          Behaviors.same
      }
    }
}
//#worker