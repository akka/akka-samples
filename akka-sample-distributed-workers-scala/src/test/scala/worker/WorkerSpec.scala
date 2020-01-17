package worker

import akka.actor.testkit.typed.TestException
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import org.scalatest.WordSpecLike
import worker.Master.Command
import worker.Master.WorkFailed
import worker.Master.WorkIsDone
import worker.Master.WorkerRequestsWork
import worker.WorkExecutor.DoWork
import worker.Worker.SubmitWork
import worker.Worker.WorkComplete

class WorkerSpec extends ScalaTestWithActorTestKit with WordSpecLike {
  val workerId = "id"
  "A Worker" should {

    "request work when work is ready" in {
      val master = createTestProbe[Command]()
      val worker = spawn(Worker(master.ref, workerId))
      worker ! Worker.WorkIsReady
      master.expectMessage(Master.WorkerRequestsWork(workerId, worker))
    }

    "report work is done until ack" in {
      val master = createTestProbe[Command]()
      val worker = spawn(Worker(master.ref, workerId))
      val workId = "work1"
      worker ! Worker.WorkIsReady
      master.expectMessageType[WorkerRequestsWork]
      worker ! SubmitWork(Work(workId, 1))
      master.expectMessageType[WorkIsDone]
      //  should retry until ack is sent
      master.expectMessageType[WorkIsDone]
      worker ! Worker.Ack(workId)
      // then worker can request more work
      master.expectMessageType[WorkerRequestsWork]
    }

    "report failure if work fails" in {

      var shouldFail = true
      val failingWorkExecutor =
        Behaviors.receiveMessage[DoWork](doWork => {
          if (shouldFail) {
            shouldFail = false
            throw TestException("oh no")
          } else {
            doWork.replyTo ! WorkComplete("cats")
            Behaviors.same
          }
        })

      val master = createTestProbe[Command]()
      val worker = spawn(Worker(master.ref, workerId, workExecutorFactory = () => failingWorkExecutor))
      val workId = "work1"
      worker ! Worker.WorkIsReady
      master.expectMessageType[WorkerRequestsWork]
      worker ! SubmitWork(Work(workId, 1))
      master.expectMessageType[WorkFailed]

      // should accept more work, with the work executor being restarted
      worker ! Worker.WorkIsReady
      master.expectMessageType[WorkerRequestsWork]
      worker ! SubmitWork(Work(workId, 1))
      master.expectMessageType[WorkIsDone]
    }
  }
}
