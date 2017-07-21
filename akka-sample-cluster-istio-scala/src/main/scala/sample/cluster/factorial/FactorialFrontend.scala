package sample.cluster.factorial

import scala.concurrent.duration._
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.routing.FromConfig
import akka.actor.ReceiveTimeout

class FactorialFrontend(upToN: Int, repeat: Boolean) extends Actor with ActorLogging {
  val backend = context.actorOf(FromConfig.props(), name = "backendRouter")

  override def preStart(): Unit = {
    sendJobs()

    if (repeat) {
      context.setReceiveTimeout(10.seconds)
    }
  }

  override def receive = {
    case (n: Int, factorial: BigInt) =>
      if (n == upToN) {
        log.debug("{}! = {}", n, factorial)
        if (repeat) sendJobs()
        else context.stop(self)
      }
    case ReceiveTimeout =>
      log.info("Timeout")
      sendJobs()
  }

  def sendJobs(): Unit = {
    log.info("Starting batch of factorials up to [{}]", upToN)
    1 to upToN foreach { backend ! _ }
  }
}
