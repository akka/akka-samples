package worker

import java.util

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorRef
import akka.actor.typed.receptionist.{ Receptionist, ServiceKey }
import akka.cluster.pubsub.{ DistributedPubSub, DistributedPubSubMediator }
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.Effect
import akka.persistence.typed.scaladsl.EventSourcedBehavior
import worker.WorkState.WorkAccepted
import worker.WorkState.WorkDomainEvent
import akka.actor.typed.scaladsl.adapter._
import worker.WorkState.WorkCompleted
import worker.WorkState.WorkStarted
import worker.WorkState.WorkerFailed

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.{ Deadline, FiniteDuration, _ }
import akka.actor.typed.delivery.ProducerController
import akka.actor.typed.delivery.WorkPullingProducerController
import akka.actor.typed.delivery.ConsumerController
import akka.actor.typed.delivery.WorkPullingProducerController.MessageWithConfirmation
import akka.actor.typed.delivery.WorkPullingProducerController.RequestNext
import akka.Done
import scala.util.Success
import scala.util.Failure
import akka.util.Timeout
import scala.collection.immutable.Queue

/**
 * The work manager actor keep tracks of all available workers, and all scheduled and ongoing work items
 */
object WorkManager {

  val ManagerServiceKey = ServiceKey[ConsumerController.Command[WorkerCommand]]("worker-service-key")

  val WorkerServiceKey: ServiceKey[WorkerCommand] = ServiceKey[WorkerCommand]("workerService")
  val ResultsTopic = "results"

  final case class Ack(workId: String) extends CborSerializable

  // Responses to requests from workers
  sealed trait WorkerCommand
  final case class DoWork(work: Work) extends WorkerCommand

  sealed trait Command extends CborSerializable
  final case class SubmitWork(work: Work, replyTo: ActorRef[WorkManager.Ack]) extends Command
  private case class RequestNextWrapper(ask: RequestNext[WorkerCommand]) extends Command
  final case class WorkIsDone(id: String) extends Command
  final case class WorkFailed(id: String, t: Throwable) extends Command
  private final case class TryStartWork() extends Command

  def apply(workTimeout: FiniteDuration): Behavior[Command] =
    Behaviors.setup { ctx =>
      Behaviors.withTimers { timers =>
        implicit val timeout = Timeout(5.seconds)
        val producerController = ctx.spawn(WorkPullingProducerController[WorkerCommand]("work-manager", ManagerServiceKey, None), "producer-controller")
        val requestNextAdapter = ctx.messageAdapter(RequestNextWrapper)
        producerController ! WorkPullingProducerController.Start(requestNextAdapter)

        var requestNext = Queue[RequestNext[WorkerCommand]]()

        def tryStartWork(workState: WorkState): Effect[WorkDomainEvent, WorkState] = {
          if (workState.hasWork) {
            requestNext match {
              case next +: xs =>
                val work = workState.nextWork
                ctx.ask[MessageWithConfirmation[WorkerCommand], Done](next.askNextTo, done => MessageWithConfirmation(DoWork(work), done)) {
                  case Success(Done) =>
                    WorkIsDone(work.workId)
                  case Failure(t) =>
                    ctx.log.error("Work failed", t)
                    WorkFailed(work.workId, t)
                }
                requestNext = xs
                Effect.persist(WorkStarted(work.workId))
              case _ =>
                Effect.none
            }
          } else {
            Effect.none
          }
        }

        EventSourcedBehavior[Command, WorkDomainEvent, WorkState](
          persistenceId = PersistenceId.ofUniqueId("master"),
          emptyState = WorkState.empty,
          commandHandler = (workState, command) => {
            command match {
              // TODO, can there be multiple outstannding work requests?
              case RequestNextWrapper(rn) =>
                requestNext = requestNext.enqueue(rn)
                tryStartWork(workState)
              case TryStartWork() =>
                tryStartWork(workState)
              case WorkIsDone(workId) =>
                 Effect.persist[WorkDomainEvent, WorkState](WorkCompleted(workId)).thenRun { newState =>
                   // FIXME, how to get the result back?
                   // publish it from the worker?
                   // Ack back to original sender
                   //
                   // No need to ack back to the woker any more
                   ctx.log.info("Work is done {}. New state {}", workId, newState)
                 }

              case WorkFailed(id, _) =>
                // Do something?
                tryStartWork(workState)
              case work: SubmitWork =>
                // idempotent
                if (workState.isAccepted(work.work.workId)) {
                  work.replyTo ! WorkManager.Ack(work.work.workId)
                  Effect.none
                } else {
                  ctx.log.info("Accepted work: {}", work.work.workId)
                  Effect.persist(WorkAccepted(work.work)).thenRun { workState =>
                    // Ack back to original sender
                    work.replyTo ! WorkManager.Ack(work.work.workId)
                    ctx.self ! TryStartWork()
                  }
                }
            }
          },
          eventHandler = (workState, event) => workState.updated(event))
      }
    }

}
