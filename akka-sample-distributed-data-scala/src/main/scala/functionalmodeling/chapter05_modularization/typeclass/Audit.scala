package functionalmodeling.chapter05_modularization
package typeclass

import java.util.{Calendar, Date}

import scala.util.{Success, Try}

/**
  * type class
  */
trait Show[T] {
  def shows(t: T): Try[String]
}

object common {
  type Amount = BigDecimal

  val today = Calendar.getInstance().getTime
}

import common._

case class Balance(amount: Amount = 0)

case class Account(no: String, name: String, dateOfOpening: Date = today, dateOfClosing: Option[Date] = None,
                   balance: Balance = Balance()) extends Show[Account] {
  def shows(a: Account) = Success(a.toString)
}

case class Account1(no: String, name: String, dateOfOpening: Date = today, dateOfClosing: Option[Date] = None,
                   balance: Balance = Balance())

trait ShowProtocol {
  implicit val showAccount: Show[Account1]
}

trait DomainShowProtocol extends ShowProtocol {
  implicit val showAccount: Show[Account1] = new Show[Account1] {
    override def shows(t: Account1) = Success(t.toString)
  }
}

object DomainShowProtocol extends DomainShowProtocol

object Reporting {
  def report[T: Show](as: Seq[T]) = as.map(implicitly[Show[T]].shows)
}

object App {
  import DomainShowProtocol._
  import Reporting._

  def main(args: Array[String]): Unit = {
    val as = Seq(
      Account1("a-1", "name-1"),
      Account1("a-2", "name-2"),
      Account1("a-3", "name-3"),
      Account1("a-4", "name-4"),
      Account1("a-5", "name-5")
    )
    
    val r = report(as)
    println(r)
  }
}