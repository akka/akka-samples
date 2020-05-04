package worker

import scala.collection.immutable.Queue

object WorkState {

  def empty: WorkState =
    WorkState(
      pendingWork = Queue.empty,
      workInProgress = Map.empty,
      acceptedWorkIds = Set.empty,
      doneWorkIds = Set.empty)

  trait WorkDomainEvent extends CborSerializable
  case class WorkAccepted(work: Work) extends WorkDomainEvent
  case class WorkStarted(workId: String) extends WorkDomainEvent
  case class WorkCompleted(workId: String) extends WorkDomainEvent
  case class WorkerFailed(workId: String) extends WorkDomainEvent
  case class WorkerTimedOut(workId: String) extends WorkDomainEvent
  case object WorkInProgressReset extends WorkDomainEvent
}

case class WorkState private (
    private val pendingWork: Queue[Work],
    private val workInProgress: Map[String, Work],
    private val acceptedWorkIds: Set[String],
    private val doneWorkIds: Set[String]) {

  import WorkState._

  def hasWork: Boolean = pendingWork.nonEmpty
  def nextWork: Work = pendingWork.head
  def isAccepted(workId: String): Boolean = acceptedWorkIds.contains(workId)
  def isInProgress(workId: String): Boolean = workInProgress.contains(workId)
  def isDone(workId: String): Boolean = doneWorkIds.contains(workId)

  def updated(event: WorkDomainEvent): WorkState = event match {
    case WorkAccepted(work) =>
      copy(pendingWork = pendingWork.enqueue(work), acceptedWorkIds = acceptedWorkIds + work.workId)

    case WorkStarted(workId) =>
      val (work, rest) = pendingWork.dequeue
      require(workId == work.workId, s"WorkStarted expected workId $workId == ${work.workId}")
      copy(pendingWork = rest, workInProgress = workInProgress + (workId -> work))

    case WorkCompleted(workId) =>
      copy(workInProgress = workInProgress - workId, doneWorkIds = doneWorkIds + workId)

    case WorkerFailed(workId) =>
      copy(pendingWork = pendingWork.enqueue(workInProgress(workId)), workInProgress = workInProgress - workId)

    case WorkerTimedOut(workId) =>
      copy(pendingWork = pendingWork.enqueue(workInProgress(workId)), workInProgress = workInProgress - workId)

    case WorkInProgressReset =>
      copy(pendingWork = pendingWork.enqueueAll(workInProgress.values), workInProgress = Map.empty)

  }

}
