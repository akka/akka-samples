package akka_tut.fsm

import akka.actor.Actor
import akka.event.LoggingReceive
import akka_tut.fsm.BankAccount.Deposit

/**
  * https://cloudmark.github.io/FSM/
  */
object BankAccount {

  case class Deposit(amount: BigInt) {
    require(amount > 0)
  }

  case class Withdraw(amount: BigInt) {
    require(amount > 0)
  }

  case object Done

  case object Failed
}

class BankAccount extends Actor {

  import BankAccount._

  var balance = BigInt(0)
  override def receive: Receive = LoggingReceive {
    case Deposit(amount) =>
      balance += amount
      sender() ! Done

    case Withdraw(amount) if amount <= balance =>
      balance -= amount
      sender() ! Done

    case _ =>
      sender() ! Failed
  }
}
