package functionalmodeling.chapter08_persistence
package cqrs
package pure

import java.util._

import scalaz._
import Scalaz._

object common {
  type Amount = BigDecimal

  val today = Calendar.getInstance().getTime
}

import common._

case class Balance(amount: Amount = 0)

case class Account(no: String, name: String, dateOfOpening: Date = today, dateOfClosing: Option[Date] = None,
                   balance: Balance = Balance())

object Account {
  implicit val showAccount: Show[Account] = Show.shows { case a: Account => a.toString }
}
