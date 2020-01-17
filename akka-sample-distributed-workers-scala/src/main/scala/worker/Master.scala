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

/**
 * The master actor keep tracks of all available workers, and all scheduled and ongoing work items
 */
object Master {

  val WorkerServiceKey: ServiceKey[Worker.Command] = ServiceKey[Worker.Command]("workerService")

  sealed trait Command extends CborSerializable
  // Messages from Workers
  case class UpdatedWorkers(workers: Receptionist.Listing) extends Command
  case class WorkerRequestsWork(workerId: String, replyTo: ActorRef[Worker.Command]) extends Command
  case class WorkIsDone(workerId: String, workId: String, result: Any, replyTo: ActorRef[Worker.Command])
      extends Command
  case class WorkFailed(worker: ActorRef[Worker.Command], workId: String) extends Command

  // External commands
  case class SubmitWork(work: Work, replyTo: ActorRef[Master.Ack]) extends Command

  def apply(workTimeout: FiniteDuration): Behavior[Command] =
    Behaviors.setup { ctx =>
      Behaviors.withTimers { timers =>
        // No typed pub sub yet
        val mediator = DistributedPubSub(ctx.system.toClassic).mediator

        // the set of available workers is not event sourced as it depends on the current set of workers
        var workers = Map[ActorRef[Worker.Command], WorkerState]()

        def notifyWorkers(workState: WorkState): Unit =
          if (workState.hasWork) {
            workers.foreach {
              case (_, WorkerState(ref, Idle)) =>
                ref ! Worker.WorkIsReady
              case _ => // busy
            }
          }

        def changeWorkerToIdle(worker: ActorRef[Worker.Command], workId: String): Unit =
          workers.get(worker) match {
            case Some(workerState @ WorkerState(_, Busy(`workId`, _))) =>
              val newWorkerState = workerState.copy(status = Idle)
              workers += (worker -> newWorkerState)
            case _ =>
            // ok, might happen after standby recovery, worker state is not persisted
          }

        val listingResponseAdapter = ctx.messageAdapter[Receptionist.Listing](UpdatedWorkers)
        ctx.system.receptionist ! Receptionist.Subscribe(Master.WorkerServiceKey, listingResponseAdapter)

        EventSourcedBehavior[Command, WorkDomainEvent, WorkState](
          persistenceId = PersistenceId.ofUniqueId("master"),
          emptyState = WorkState.empty,
          commandHandler = (workState, command) => {
            command match {
              case UpdatedWorkers(listing) =>
                val newWorkerList: Set[ActorRef[Worker.Command]] =
                  listing.allServiceInstances(Master.WorkerServiceKey)

                var events = ListBuffer.empty[WorkDomainEvent]
                val removedWorkers = workers.keySet.diff(newWorkerList)
                val newWorkers = newWorkerList.diff(workers.keySet)
                // for each removed, check if they had work in progress
                removedWorkers.foreach { removedWorkers =>
                  workers(removedWorkers) match {
                    case WorkerState(_, Idle) =>
                    // that's fine, nothing in progress
                    case WorkerState(_, Busy(workId, _)) =>
                      // work considered failed
                      events += WorkerFailed(workId)
                  }
                  workers -= removedWorkers
                }

                // update the current members
                newWorkers.foreach { newWorker =>
                  workers += (newWorker -> WorkerState(newWorker, Idle))
                }
                ctx.log.info("Workers updated. Removed workers: {}. New workers: {}", removedWorkers, newWorkers)
                ctx.log.info("All workers: {}", workers)

                if (workState.hasWork) {
                  newWorkers.foreach { newWorker =>
                    newWorker ! Worker.WorkIsReady
                  }
                }
                if (events.nonEmpty) {
                  Effect.persist(events.toList).thenRun(notifyWorkers)
                } else {
                  Effect.none
                }
              case work: SubmitWork =>
                // idempotent
                if (workState.isAccepted(work.work.workId)) {
                  work.replyTo ! Master.Ack(work.work.workId)
                  Effect.none
                } else {
                  ctx.log.info("Accepted work: {}", work.work.workId)
                  Effect.persist(WorkAccepted(work.work)).thenRun { _ =>
                    // Ack back to original sender
                    work.replyTo ! Master.Ack(work.work.workId)
                    notifyWorkers(workState)
                  }
                }
              case WorkerRequestsWork(workerId, replyTo) =>
                ctx.log.info("Worker {} requesting work", workerId)

                if (workState.hasWork) {
                  workers.get(replyTo) match {
                    case Some(workerState @ WorkerState(_, Idle)) =>
                      val work = workState.nextWork
                      Effect.persist[WorkDomainEvent, WorkState](WorkStarted(work.workId)).thenRun { _ =>
                        ctx.log.info("Giving worker {} some work {}", workerId, work.workId)
                        val newWorkerState = workerState.copy(status = Busy(work.workId, Deadline.now + workTimeout))
                        workers += (replyTo -> newWorkerState)
                        replyTo ! Worker.SubmitWork(work)
                      }
                    case _ =>
                      Effect.none[WorkDomainEvent, WorkState]
                  }
                } else {
                  Effect.none[WorkDomainEvent, WorkState]
                }
              case WorkIsDone(workerId, workId, result, replyTo) =>
                // idempotent - redelivery from the worker may cause duplicates, so it needs to be
                if (workState.isDone(workId)) {
                  // previous Ack was lost, confirm again that this is done
                  replyTo ! Worker.Ack(workId)
                  Effect.none
                } else if (!workState.isInProgress(workId)) {
                  ctx.log.info("Work {} not in progress, reported as done by worker {}", workId, workerId)
                  Effect.none
                } else {
                  ctx.log.info("Work {} is done by worker {}", result, workerId)
                  changeWorkerToIdle(replyTo, workId)
                  Effect.persist[WorkDomainEvent, WorkState](WorkCompleted(workId, result)).thenRun { _ =>
                    mediator ! DistributedPubSubMediator.Publish(ResultsTopic, WorkResult(workId, result))
                    // Ack back to original sender
                    replyTo ! Worker.Ack(workId)
                  }
                }
              case WorkFailed(workerId, workId) =>
                if (workState.isInProgress(workId)) {
                  ctx.log.info("Work {} failed by worker {}", workId, workerId)
                  changeWorkerToIdle(workerId, workId)
                  Effect.persist[WorkDomainEvent, WorkState](WorkerFailed(workId)).thenRun(notifyWorkers)
                } else {
                  Effect.none[WorkDomainEvent, WorkState]
                }
            }
          },
          eventHandler = (workState, event) => workState.updated(event))
      }
    }

  val ResultsTopic = "results"

  case class Ack(workId: String) extends CborSerializable

  sealed trait WorkerStatus
  case object Idle extends WorkerStatus
  case class Busy(workId: String, deadline: Deadline) extends WorkerStatus
  case class WorkerState(ref: ActorRef[Worker.Command], status: WorkerStatus)

}
