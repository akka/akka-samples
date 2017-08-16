package functionalmodeling.chapter03_designing.repository

import java.util.{ Date, Calendar }

object common {
  type Amount = BigDecimal

  val today = Calendar.getInstance.getTime
}

import common._

case class Balance(amount: Amount = 0)

case class Account(no: String, name: String, dateOfOpening: Date = today, dateOfClosing: Option[Date] = None,
                   balance: Balance = Balance(0))
