package worker

import java.util.UUID

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import worker.Master.WorkerRequestsWork
import worker.WorkExecutor.DoWork
import worker.Worker.Ack
import worker.Worker.Register
import worker.Worker.SubmitWork
import worker.Worker.WorkIsReady
import worker.Worker.WorkTimeout

import scala.concurrent.duration._

class Worker private (workerId: String,
                      masterProxy: ActorRef[Master.Command],
                      ctx: ActorContext[Worker.Command],
                      timers: TimerScheduler[Worker.Command],
                      workExecutorFactory: () => Behavior[DoWork]) {
  private val registerInterval = ctx.system.settings.config
    .getDuration("distributed-workers.worker-registration-interval")
    .toNanos
    .nano

  private val workAckTimeout = ctx.system.settings.config
    .getDuration("distributed-workers.work-ack-timeout")
    .toNanos
    .nano

  timers.startTimerWithFixedDelay("register", Register, registerInterval)

  def createWorkExecutor(): ActorRef[DoWork] = {
    val supervised = Behaviors
      .supervise(workExecutorFactory())
      .onFailure[Exception](SupervisorStrategy.stop)
    val ref = ctx.spawn(supervised, "work-executor")
    ctx.watch(ref)
    ref
  }

  def deregisterOnStop()
    : PartialFunction[(scaladsl.ActorContext[Worker.Command], Signal), Behavior[
      Worker.Command
    ]] = {
    case (_, PostStop) =>
      ctx.log.info("Worker has stopped, de-registering")
      masterProxy ! Master.DeRegisterWorker(workerId)
      Behaviors.same
  }

  def reportWorkFailedOnRestart(
    workId: String
  ): PartialFunction[(scaladsl.ActorContext[Worker.Command], Signal), Behavior[
    Worker.Command
  ]] = {
    case (_, Terminated(_)) =>
      ctx.log.info("Work executor terminated. Reporting failure")
      masterProxy ! Master.WorkFailed(workerId, workId)
      // need to re-create the work executor
      idle(createWorkExecutor())
  }

  def idle(
    workExecutor: ActorRef[DoWork] = createWorkExecutor()
  ): Behavior[Worker.Command] =
    Behaviors.setup[Worker.Command] { ctx =>
      Behaviors.receiveMessagePartial[Worker.Command] {
        case Register =>
          masterProxy ! Master.RegisterWorker(workerId, ctx.self)
          Behaviors.same
        case WorkIsReady =>
          // this is the only state where we reply to WorkIsReady
          masterProxy ! WorkerRequestsWork(
            workerId,
            ctx.self.narrow[SubmitWork]
          )
          Behaviors.same

        case SubmitWork(Work(workId, job: Int)) =>
          ctx.log.info("Got work: {}", job)
          workExecutor ! WorkExecutor.DoWork(job, ctx.self)
          working(workId, workExecutor)

        case Ack(_) =>
          Behaviors.same

      } receiveSignal deregisterOnStop()
    }

  def working(workId: String,
              workExecutor: ActorRef[DoWork]): Behavior[Worker.Command] =
    Behaviors.setup { ctx =>
      Behaviors.receiveMessagePartial[Worker.Command] {
        case Worker.WorkComplete(result) =>
          ctx.log.info("Work is complete. Result {}.", result)
          masterProxy ! Master
            .WorkIsDone(workerId, workId, result, ctx.self.narrow[Worker.Ack])
          ctx.setReceiveTimeout(workAckTimeout, WorkTimeout)
          waitForWorkIsDoneAck(result, workId, workExecutor)

        case _: SubmitWork =>
          ctx.log.warn(
            "Yikes. Master told me to do work, while I'm already working."
          )
          Behaviors.unhandled

        case Register =>
          masterProxy ! Master.RegisterWorker(workerId, ctx.self)
          Behaviors.same
      } receiveSignal (deregisterOnStop()
        .orElse(reportWorkFailedOnRestart(workId)))
    }

  def waitForWorkIsDoneAck(
    result: String,
    workId: String,
    workExecutor: ActorRef[DoWork]
  ): Behavior[Worker.Command] =
    Behaviors.setup { ctx =>
      Behaviors.receiveMessage[Worker.Command] {
        case WorkTimeout =>
          ctx.log.info("No ack from master, resending work result")
          masterProxy ! Master
            .WorkIsDone(workerId, workId, result, ctx.self.narrow[Ack])
          Behaviors.same
        case Ack(id) if id == workId =>
          ctx.log.info("Work acked")
          masterProxy ! WorkerRequestsWork(
            workerId,
            ctx.self.narrow[SubmitWork]
          )
          ctx.cancelReceiveTimeout()
          idle(workExecutor)

        case Register =>
          masterProxy ! Master.RegisterWorker(workerId, ctx.self)
          Behaviors.same
      } receiveSignal (deregisterOnStop()
        .orElse(reportWorkFailedOnRestart(workId)))

    }

}

/**
  * The worker is actually more of a middle manager, delegating the actual work
  * to the WorkExecutor, supervising it and keeping itself available to interact with the work master.
  */
object Worker {

  sealed trait Command extends CborSerializable
  case object WorkIsReady extends Command
  case class Ack(id: String) extends Command
  case class SubmitWork(work: Work) extends Command
  case class WorkComplete(result: String) extends Command

  private case object Register extends Command
  private case object WorkTimeout extends Command

  def apply(
             masterProxy: ActorRef[Master.Command],
             workerId: String = UUID.randomUUID().toString,
             workExecutorFactory: () => Behavior[DoWork] = () => WorkExecutor()
  ): Behavior[Command] = Behaviors.setup[Command] { ctx =>
    Behaviors.withTimers { timers: TimerScheduler[Command] =>
      masterProxy ! Master.RegisterWorker(workerId, ctx.self)

      Behaviors
        .supervise(
          new Worker(workerId, masterProxy, ctx, timers, workExecutorFactory)
            .idle()
        )
        .onFailure[Exception](SupervisorStrategy.restart)
    }
  }

}
