package worker

import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

import akka.actor.typed._
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout
import worker.Master.SubmitWork

import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success

/**
  * Dummy front-end that periodically sends a workload to the master.
  */
// #front-end
object FrontEnd {

  sealed trait Command
  case object Tick extends Command
  case object Failed extends Command
  case object Retry extends Command
  case object WorkAccepted extends Command

  private def nextWorkId(): String = UUID.randomUUID().toString

  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    val masterProxy = MasterSingleton.init(ctx.system)
    idle(0, masterProxy)
  }

  def idle(workCounter: Int,
           masterProxy: ActorRef[SubmitWork]): Behavior[Command] =
    Behaviors.withTimers { timer =>
      val nextTick = ThreadLocalRandom.current.nextInt(3, 10).seconds
      timer.startSingleTimer("tick", Tick, nextTick)
      Behaviors.receiveMessage {
        case Tick =>
          busy(workCounter + 1, Work(nextWorkId(), workCounter), masterProxy)
        case _ =>
          Behaviors.unhandled
      }
    }

  def busy(workCounter: Int,
           workInProgress: Work,
           masterProxy: ActorRef[SubmitWork]): Behavior[Command] =
    Behaviors.withTimers { timers =>
      Behaviors.setup { ctx =>
        def sendWork(work: Work): Unit = {
          implicit val timeout: Timeout = Timeout(5.seconds)
          ctx.ask[SubmitWork, Master.Ack](masterProxy, reply => SubmitWork(work, reply)) {
            case Success(_) => WorkAccepted
            case Failure(_) => Failed
          }
        }

        sendWork(workInProgress)

        Behaviors.receiveMessage {
          case Failed =>
            ctx.log.info("Work {} not accepted, retry after a while", workInProgress.workId)
            timers.startSingleTimer("retry", Retry, 3.seconds)
            Behaviors.same
          case WorkAccepted =>
            ctx.log.info("Got ack for workId {}", workInProgress.workId)
            idle(workCounter, masterProxy)
          case Retry =>
            ctx.log.info("Retrying work {}", workInProgress.workId)
            sendWork(workInProgress)
            Behaviors.same
          case Tick =>
            Behaviors.unhandled
        }
      }
    }

}
// #front-end
