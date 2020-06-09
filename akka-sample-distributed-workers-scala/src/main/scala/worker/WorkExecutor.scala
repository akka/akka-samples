package worker

import java.util.concurrent.ThreadLocalRandom

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import worker.Worker.WorkComplete

import scala.concurrent.duration._

/**
  * Work executor is the actor actually performing the work.
  */
object WorkExecutor {
  case class ExecuteWork(n: Int, replyTo: ActorRef[WorkComplete])

  def apply(): Behavior[ExecuteWork] = {
    Behaviors.setup { ctx =>
      Behaviors.receiveMessage { doWork =>
        ctx.log.info("Doing work {}", doWork)
        val n = doWork.n
        val n2 = n * n
        val result = s"$n * $n = $n2"

        // simulate that the processing time varies
        val randomProcessingTime = ThreadLocalRandom.current.nextInt(1, 3).seconds

        ctx.scheduleOnce(
          randomProcessingTime,
          doWork.replyTo,
          WorkComplete(result)
        )

        Behaviors.same
      }
    }
  }
}
