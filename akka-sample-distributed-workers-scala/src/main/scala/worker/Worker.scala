package worker

import java.util.UUID

import akka.actor.typed._
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl._
import worker.WorkManager
import worker.WorkManager.WorkerRequestsWork
import worker.WorkExecutor.ExecuteWork
import worker.Worker.Ack
import worker.Worker.SubmitWork
import worker.Worker.WorkIsReady
import worker.Worker.WorkTimeout

import scala.concurrent.duration._

/**
 * The worker is actually more of a middle manager, delegating the actual work
 * to the WorkExecutor, supervising it and keeping itself available to interact with the work manager.
 */
object Worker {

  sealed trait Message extends CborSerializable
  case object WorkIsReady extends Message
  case class Ack(id: String) extends Message
  case class SubmitWork(work: Work) extends Message
  case class WorkComplete(result: String) extends Message

  private case object WorkTimeout extends Message

  def apply(
      workManagerProxy: ActorRef[WorkManager.Command],
      workerId: String = UUID.randomUUID().toString,
      workExecutorFactory: () => Behavior[ExecuteWork] = () => WorkExecutor()): Behavior[Message] =
    Behaviors.setup[Message] { ctx =>
      Behaviors.withTimers { timers: TimerScheduler[Message] =>
        val workCommandAdapter = ctx.messageAdapter[WorkManager.WorkerCommand] {
          case WorkManager.DoWork(work) => SubmitWork(work)
          case WorkManager.WorkAck(id) => Ack(id)
          case WorkManager.WorkAvailable => WorkIsReady
        }
        ctx.system.receptionist ! Receptionist.Register(WorkManager.WorkerServiceKey, workCommandAdapter)
        Behaviors
          .supervise(new Worker(workerId, workCommandAdapter, workManagerProxy, ctx, timers, workExecutorFactory).idle())
          .onFailure[Exception](SupervisorStrategy.restart)
      }
    }

}

class Worker private (
    workerId: String,
    adapter: ActorRef[WorkManager.WorkerCommand],
    workManagerProxy: ActorRef[WorkManager.Command],
    ctx: ActorContext[Worker.Message],
    timers: TimerScheduler[Worker.Message],
    workExecutorFactory: () => Behavior[ExecuteWork]) {

  private val workAckTimeout =
    ctx.system.settings.config.getDuration("distributed-workers.work-ack-timeout").toNanos.nano

  def createWorkExecutor(): ActorRef[ExecuteWork] = {
    val supervised = Behaviors.supervise(workExecutorFactory()).onFailure[Exception](SupervisorStrategy.stop)
    val ref = ctx.spawn(supervised, "work-executor")
    ctx.watch(ref)
    ref
  }

  def reportWorkFailedOnRestart(
      workId: String): PartialFunction[(scaladsl.ActorContext[Worker.Message], Signal), Behavior[Worker.Message]] = {
    case (_, Terminated(_)) =>
      ctx.log.info("Work executor terminated. Reporting failure")
      workManagerProxy ! WorkManager.WorkFailed(adapter, workId)
      // need to re-create the work executor
      idle(createWorkExecutor())
  }

  def idle(workExecutor: ActorRef[ExecuteWork] = createWorkExecutor()): Behavior[Worker.Message] =
    Behaviors.setup[Worker.Message] { ctx =>
      Behaviors.receiveMessagePartial[Worker.Message] {
        case WorkIsReady =>
          // this is the only state where we reply to WorkIsReady
          workManagerProxy ! WorkerRequestsWork(workerId, adapter)
          Behaviors.same

        case SubmitWork(Work(workId, job: Int)) =>
          ctx.log.info("Got work: {}", job)
          workExecutor ! WorkExecutor.ExecuteWork(job, ctx.self)
          working(workId, workExecutor)

        case Ack(_) =>
          Behaviors.same

      }
    }

  def working(workId: String, workExecutor: ActorRef[ExecuteWork]): Behavior[Worker.Message] =
    Behaviors.setup { ctx =>
      Behaviors
        .receiveMessagePartial[Worker.Message] {
          case Worker.WorkComplete(result) =>
            ctx.log.info("Work is complete. Result {}.", result)
            workManagerProxy ! WorkManager.WorkIsDone(workerId, workId, result, adapter)
            ctx.setReceiveTimeout(workAckTimeout, WorkTimeout)
            waitForWorkIsDoneAck(result, workId, workExecutor)

          case _: SubmitWork =>
            ctx.log.warn("Yikes. WorkManager told me to do work, while I'm already working.")
            Behaviors.unhandled

        }
        .receiveSignal(reportWorkFailedOnRestart(workId))
    }

  def waitForWorkIsDoneAck(result: String, workId: String, workExecutor: ActorRef[ExecuteWork]): Behavior[Worker.Message] =
    Behaviors.setup { ctx =>
      Behaviors
        .receiveMessage[Worker.Message] {
          case WorkTimeout =>
            ctx.log.info("No ack from master, resending work result")
            workManagerProxy ! WorkManager.WorkIsDone(workerId, workId, result, adapter)
            Behaviors.same
          case Ack(id) if id == workId =>
            ctx.log.info("Work acked")
            workManagerProxy ! WorkerRequestsWork(workerId, adapter)
            ctx.cancelReceiveTimeout()
            idle(workExecutor)
        }
        .receiveSignal(reportWorkFailedOnRestart(workId))

    }

}
