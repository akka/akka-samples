package worker

import java.util.UUID

import akka.actor.typed._
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl._
import worker.WorkManager
import worker.WorkExecutor.ExecuteWork
import worker.Worker.WorkTimeout

import scala.concurrent.duration._
import akka.actor.typed.delivery.ConsumerController
import akka.actor.typed.delivery.ProducerController

/**
 * The worker is actually more of a middle manager, delegating the actual work
 * to the WorkExecutor, supervising it and keeping itself available to interact with the work manager.
 */
object Worker {

  sealed trait Message extends CborSerializable
  case class DeliveredMessage(confirmTo: ActorRef[ConsumerController.Confirmed], message: WorkManager.WorkerCommand, seqNr: Long) extends Message
  case class WorkComplete(result: String) extends Message
  case class WorkTimeout() extends Message

  private case object WorkTimeout extends Message

  def apply(
      workerId: String = UUID.randomUUID().toString,
      workExecutorFactory: () => Behavior[ExecuteWork] = () => WorkExecutor()): Behavior[Message] =
    Behaviors.setup[Message] { ctx =>
        val consumerController = ctx.spawn(ConsumerController[WorkManager.WorkerCommand](WorkManager.ManagerServiceKey), "consumer-controller")
        val deliverAdapter = ctx.messageAdapter[ConsumerController.Delivery[WorkManager.WorkerCommand]](d => DeliveredMessage(d.confirmTo, d.msg, d.seqNr))
        consumerController ! ConsumerController.Start(deliverAdapter)
        Behaviors
          .supervise(new Worker(workerId, ctx, workExecutorFactory).idle())
          .onFailure[Exception](SupervisorStrategy.restart)
    }

}

class Worker private (
    workerId: String,
    ctx: ActorContext[Worker.Message],
    workExecutorFactory: () => Behavior[ExecuteWork]) {

  import Worker._

  def createWorkExecutor(): ActorRef[ExecuteWork] = {
    val supervised = Behaviors.supervise(workExecutorFactory()).onFailure[Exception](SupervisorStrategy.stop)
    val ref = ctx.spawn(supervised, "work-executor")
    ctx.watch(ref)
    ref
  }

  def idle(workExecutor: ActorRef[ExecuteWork] = createWorkExecutor()): Behavior[Worker.Message] =
      Behaviors.receiveMessagePartial[Worker.Message] {
        case DeliveredMessage(confirmTo, message, seqNr) =>
          message match {
            case WorkManager.DoWork(w@Work(workId, job: Int)) =>
              ctx.log.info("Got work: {}", w)
              workExecutor ! WorkExecutor.ExecuteWork(job, ctx.self)
              working(workId, workExecutor, confirmTo, seqNr)
          }
      }

  def working(workId: String, workExecutor: ActorRef[ExecuteWork], confirmTo: ActorRef[ConsumerController.Confirmed], seqNr: Long): Behavior[Worker.Message] =
      Behaviors
        .receiveMessagePartial[Worker.Message] {
          case Worker.WorkComplete(result) =>
            ctx.log.info("Work is complete. Result {}. Sequence nr {}", result, seqNr)
            confirmTo ! ConsumerController.Confirmed(seqNr)
            idle(workExecutor)
          case _: DeliveredMessage =>
            ctx.log.warn("Yikes. Reliable delivery told me to do work, while I'm already working.")
            Behaviors.unhandled

        }
        .receiveSignal {
          case (_, Terminated(_)) =>
            ctx.log.info("Work executor terminated")
            // The work is confirmed meaning it won't be re-delivered. Sending back a failure would need
            // to be done explicitly
            confirmTo ! ConsumerController.Confirmed(seqNr)
            idle(createWorkExecutor())
        }

}
