package functionalmodeling.chapter08_persistence
package cqrs
package service

import lib._
import common._
import org.joda.time.DateTime

import scalaz._
import Scalaz._

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

trait RepositoryBackedInterpreter {

}
