package sample.cluster.factorial

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

import scala.annotation.tailrec

object Calculator {

  final case class CalculateFactorial(n: Int, replyTo: ActorRef[FactorialResult])
  final case class FactorialResult(n: Int, factorial: BigInt)

  def apply(): Behavior[CalculateFactorial] = Behaviors.receiveMessage {
    case CalculateFactorial(n, replyTo) =>
      replyTo ! FactorialResult(n, factorial(n))
      Behaviors.same
  }

  def factorial(n: Int): BigInt = {
    @tailrec def factorialAcc(acc: BigInt, n: Int): BigInt = {
      if (n <= 1) acc
      else factorialAcc(acc * n, n - 1)
    }
    factorialAcc(BigInt(1), n)
  }

}

