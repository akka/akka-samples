package sample.cluster.factorial.frontend

import scala.annotation.tailrec
import scala.concurrent.Future
import com.typesafe.config.ConfigFactory
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorSystem
import akka.actor.Props
import akka.pattern.pipe

class FactorialBackend extends Actor with ActorLogging {

  import context.dispatcher

  def receive = {
    case (n: Int) =>
      Future(factorial(n)) map { result => (n, result) } pipeTo sender()
  }

  def factorial(n: Int): BigInt = {
    @tailrec def factorialAcc(acc: BigInt, n: Int): BigInt = {
      if (n <= 1) acc
      else factorialAcc(acc * n, n - 1)
    }
    factorialAcc(BigInt(1), n)
  }

}

object FactorialBackend {
  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.parseString("akka.cluster.roles = [backend]").
      withFallback(ConfigFactory.load("factorial"))

    val systemName = sys.props.get("sample.cluster.actor-system-name").getOrElse(
      throw new IllegalArgumentException("Property sample.cluster.actor-system-name must be defined")
    )

    val system = ActorSystem(systemName, config)

    system.actorOf(Props[FactorialBackend], name = "factorialBackend")

    system.actorOf(Props[MetricsListener], name = "metricsListener")
  }
}