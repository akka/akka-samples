package sample.cluster.factorial

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.Routers

import scala.concurrent.duration._

object FactorialSession {

  sealed trait Command
  private final case class RegisterResult(n: Int, result: BigInt) extends Command
  case object Stop extends Command

  def apply(upToN: Int, repeat: Boolean): Behavior[Command] = Behaviors.setup { ctx =>

    val backend = ctx.spawn(Routers.group(FactorialBackend.FactorialServiceKey), "FactorialBackendRouter")

    val responseAdapter = ctx.messageAdapter[Calculator.FactorialResult](result =>
      RegisterResult(result.n, result.factorial)
    )

    sendJobs()
    if (repeat) {
      ctx.setReceiveTimeout(10.seconds, Stop)
    }

    def sendJobs(): Unit = {
      ctx.log.info("Starting batch of factorials up to [{}]", upToN)
      (1 to upToN).foreach { n =>
        backend ! Calculator.CalculateFactorial(n, responseAdapter)
      }
    }

    Behaviors.receiveMessage {
      case RegisterResult(n, factorial) =>
        if (n == upToN) {
          ctx.log.debug("{}! = {}", n, factorial)
          if (repeat) {
            sendJobs()
            Behaviors.same
          }
          else Behaviors.stopped
        } else Behaviors.same
      case Stop =>
        Behaviors.stopped
    }
  }

}

