package functionalmodeling.chapter08_persistence
package cqrs
package service

import lib._
import common._
import org.joda.time.DateTime

import scalaz._
import Scalaz._
import scalaz.concurrent.Task

// Free Monad step 1
case class Opened(no: String, name: String, openingDate: Option[DateTime], at: DateTime = today) extends Event[Account]
case class Closed(no: String, closeDate: Option[DateTime], at: DateTime = today) extends Event[Account]
case class Debited(no: String, amount: Amount, at: DateTime = today) extends Event[Account]
case class Credited(no: String, amount: Amount, at: DateTime = today) extends Event[Account]

object AccountSnapshot extends Snapshot[Account] {

  override def updateState(e: Event[_], initial: Map[String, Account]) = e match {
    case Opened(no, name, odate, at) =>
      initial + (no -> Account(no, name, odate.getOrElse(at)))

    case Closed(no, cdate, _) =>
      initial + (no -> initial(no).copy(dateOfClosing = cdate))

    case Debited(no, amount, _) =>
      val a = initial(no)
      initial + (no -> a.copy(balance = Balance(a.balance.amount - amount)))

    case Credited(no, amount, _) =>
      val a = initial(no)
      initial + (no -> a.copy(balance = Balance(a.balance.amount + amount)))

  }

}

// Free Monad step 3
trait AccountCommands extends Commands[Account] {
  import scala.language.implicitConversions

  private implicit def liftEvent[Next](event: Event[Next]): Command[Next] = Free.liftF(event)

  def open(no: String, name: String, openingDate: Option[DateTime]): Command[Account] =
    Opened(no, name, openingDate, today)

  def close(no: String, closeDate: Option[DateTime]): Command[Account] =
    Closed(no, closeDate, today)

  def debit(no: String, amount: Amount): Command[Account] =
    Debited(no, amount, today)

  def credit(no: String, amount: Amount): Command[Account] =
    Credited(no, amount, today)
}

object RepositoryBackedAccountInterpreter extends RepositoryBackedInterpreter {

  import AccountSnapshot._
  import spray.json._
  //import JSONProtocols._

  val eventLog = InMemoryEventStore.apply[String]

  import eventLog._

  // Free Monad step 5: Interpreter
  val step: Event ~> Task = new (Event ~> Task) {
    override def apply[A](action: Event[A]): Task[A] = handleCommand(action)
  }
  
  private def handleCommand[A](e: Event[A]): Task[A] = e match {
    case o @ Opened(no, name, odate, _) => Task {
      validateOpen(no).fold(
        err => throw new RuntimeException(err),
        _ => {
          val a = Account(no, name, odate.get)
          eventLog.put(no, o)
          a
        }
      )
    }

    case c @ Closed(no, cdate, _) => Task {
      validateClose(no, cdate).fold(
        err => throw new RuntimeException(err),
        currentState => {
          eventLog.put(no, c)
          updateState(c, currentState)(no)
        }
      )
    }

    case d @ Debited(no, amount, _) => Task {
      validateDebit(no, amount).fold(
        err => throw new RuntimeException(err),
        currentState => {
          eventLog.put(no, d)
          updateState(d, currentState)(no)
        }
      )
    }

    case r @ Credited(no, amount, _) => Task {
      validateCredit(no).fold(
        err => throw new RuntimeException(err),
        currentState => {
          eventLog.put(no, r)
          updateState(r, currentState)(no)
        }
      )
    }
  }

  def validateOpen(no: String) = {
    val events = eventLog.get(no)
    if (events.nonEmpty) s"Account with no $no already exists".left
    else no.right
  }

  def validateClose(no: String, cdate: Option[DateTime]) = for {
    l <- events(no)
    s <- snapshot(l)
    a <- closed(s(no))
    _ <- beforeOpeningDate(a, cdate)
  } yield s

  private def closed(a: Account): Error \/ Account =
    if (a.dateOfClosing isDefined) s"Account ${a.no} is closed".left
    else a.right

  private def beforeOpeningDate(a: Account, cd: Option[DateTime]): Error \/ Account =
    if (a.dateOfOpening isBefore cd.getOrElse(today))
      s"Can not close at a date earlier than opening date ${a.dateOfOpening}".left
    else a.right

  private def validateDebit(no: String, amount: Amount) = for {
    l <- events(no)
    s <- snapshot(l)
    a <- closed(s(no))
    _ <- sufficientFundsToDebit(a, amount)
  } yield s

  private def sufficientFundsToDebit(a: Account, amount: Amount): Error \/ Account =
    if (a.balance.amount < amount) s"insufficient fund to debit $amount from ${a.no}".left
    else a.right

  private def validateCredit(no: String) = for {
    l <- events(no)
    s <- snapshot(l)
    _ <- closed(s(no))
  } yield s
}

// Free Monad step4 : Program
object Script extends AccountCommands {
  def transfer(from: String, to: String, amount: Amount): Command[(Account, Account)] = for {
    d <- debit(from, amount)
    c <- credit(to, amount)
  } yield (d, c)

  def openAndCreditAndDebit(no: String, name: String, creditAmount: Amount, debitAmount: Amount) = for {
    _ <- open(no, name, Some(today))
    _ <- credit(no, creditAmount)
    _ <- credit(no, creditAmount)
    d <- debit(no, debitAmount)
  } yield d

  def openAndCreditThenTransfer(from: String, fromName: String, to: String, toName: String, creditAmount: Amount, transferAmount: Amount) = for {
    _ <- open(from, fromName, Some(today))
    _ <- open(to, toName, Some(today))
    _ <- credit(from, creditAmount)
    dc <- transfer(from, to, transferAmount)
  } yield dc
}

// Free Monad step 6: run
object BankApp {
  def main(args: Array[String]): Unit = {
    import Script._
    import RepositoryBackedAccountInterpreter._

    val no1 = "a-001"
    val no2 = "a-002"
    val name = "Jame LeBron"
    val name2 = "Wade"
    val creditAmount1 = 20000
    val creditAmount2 = 10000
    val debitAmount1 = 30000
    val transferAmount = 15000

    /*val r1 = RepositoryBackedAccountInterpreter(openAndCreditAndDebit(no1, name, creditAmount1, debitAmount1))
    println(r1.unsafePerformSync)
    println(eventLog.events(no1))
*/
    val rt = RepositoryBackedAccountInterpreter(openAndCreditThenTransfer(no1, name, no2, name2, creditAmount1, transferAmount))
    println(rt.unsafePerformSync)
    println(eventLog.eventLog(no1))
    println(eventLog.eventLog(no2))

    /*val r2 = RepositoryBackedAccountInterpreter(openAndCreditAndDebit(no2, name, creditAmount2, debitAmount1))
    println(r2.unsafePerformSync)
    println(eventLog.events(no2))*/
  }
}