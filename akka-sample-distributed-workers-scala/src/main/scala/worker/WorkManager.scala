package worker

import akka.Done
import akka.actor.typed.delivery.WorkPullingProducerController.{MessageWithConfirmation, RequestNext}
import akka.actor.typed.delivery.{ConsumerController, WorkPullingProducerController}
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.{PersistenceId, RecoveryCompleted}
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import akka.util.Timeout
import worker.WorkState.{WorkAccepted, WorkCompleted, WorkDomainEvent, WorkStarted, WorkInProgressReset}

import scala.concurrent.duration.{FiniteDuration, _}
import scala.util.{Failure, Success}

/**
  * The work manager actor keep tracks of all available workers, and all scheduled and ongoing work items
  */
object WorkManager {

  val ManagerServiceKey = ServiceKey[ConsumerController.Command[WorkerCommand]]("worker-service-key")

  val WorkerServiceKey: ServiceKey[WorkerCommand] =
    ServiceKey[WorkerCommand]("workerService")
  val ResultsTopic = "results"

  final case class Ack(workId: String) extends CborSerializable

  // Responses to requests from workers
  sealed trait WorkerCommand extends CborSerializable
  final case class DoWork(work: Work) extends WorkerCommand with CborSerializable

  sealed trait Command
  final case class SubmitWork(work: Work, replyTo: ActorRef[WorkManager.Ack])
      extends Command with CborSerializable
  private case class RequestNextWrapper(ask: RequestNext[WorkerCommand]) extends Command
  final case class WorkIsDone(id: String) extends Command
  final case class WorkFailed(id: String, t: Throwable) extends Command
  private case object TryStartWork extends Command
  private case object ResetWorkInProgress extends Command

  def apply(workTimeout: FiniteDuration): Behavior[Command] =
    Behaviors.setup { ctx =>
        implicit val timeout = Timeout(5.seconds)
        val producerController = ctx.spawn(WorkPullingProducerController[WorkerCommand]("work-manager", ManagerServiceKey, None), "producer-controller")
        val requestNextAdapter = ctx.messageAdapter(RequestNextWrapper)
        producerController ! WorkPullingProducerController.Start(requestNextAdapter)

        var requestNext: Option[RequestNext[WorkerCommand]] = None

        def tryStartWork(workState: WorkState): Effect[WorkDomainEvent, WorkState] = {

          if (workState.hasWork) {
            requestNext match {
              case Some(next) =>
                val work = workState.nextWork
                ctx.ask[MessageWithConfirmation[WorkerCommand], Done](next.askNextTo, done => MessageWithConfirmation(DoWork(work), done)) {
                  case Success(Done) =>
                    WorkIsDone(work.workId)
                  case Failure(t) =>
                    ctx.log.error("Work failed", t)
                    WorkFailed(work.workId, t)
                }
                requestNext = None
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
              case RequestNextWrapper(rn) =>
                ctx.log.info("work request: {}")
                if (requestNext.isDefined) {
                  throw new IllegalStateException(s"Request next when there is already demand ${rn}, ${requestNext}")
                }
                requestNext = Some(rn)
                tryStartWork(workState)
              case TryStartWork =>
                tryStartWork(workState)
              case ResetWorkInProgress =>
                Effect.persist(WorkInProgressReset)
              case WorkIsDone(workId) =>
                 Effect.persist[WorkDomainEvent, WorkState](WorkCompleted(workId)).thenRun { newState =>
                   ctx.log.info("Work is done {}. New state {}", workId, newState)
                 }

              case WorkFailed(id, reason) =>
                ctx.log.info("Work failed {} {}", id, reason)
                tryStartWork(workState)
              case work: SubmitWork =>
                // idempotent
                if (workState.isAccepted(work.work.workId)) {
                  work.replyTo ! WorkManager.Ack(work.work.workId)
                  Effect.none
                } else {
                  ctx.log.info("Accepted work: {}", work.work.workId)
                  Effect.persist(WorkAccepted(work.work)).thenRun { _ =>
                    // Ack back to original sender
                    work.replyTo ! WorkManager.Ack(work.work.workId)
                    ctx.self ! TryStartWork
                  }
                }
            }
          },
          eventHandler = (workState, event) => workState.updated(event)
        ).receiveSignal {
          case (state, RecoveryCompleted) =>
            // Any in progress work from the previous incarnation is retried
            ctx.self ! ResetWorkInProgress
        }
      }

}
