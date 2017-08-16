package functionalmodeling.chapter03_designing.algebra.interpreter

import java.util._

object common {
  type Amount = BigDecimal

  def today = Calendar.getInstance.getTime
}

import functionalmodeling.chapter03_designing.algebra.interpreter.common._

case class Balance(amount: Amount = 0)

case class Account(no: String, name: String, dateOfOpening: Date = today, dateOfClosing: Option[Date] = None,
                   balance: Balance = Balance(0))
