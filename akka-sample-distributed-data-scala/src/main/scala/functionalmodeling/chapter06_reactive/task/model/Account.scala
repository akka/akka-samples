package functionalmodeling.chapter06_reactive
package task
package model

import java.util._

import scalaz.Scalaz._

/**
  *
  */
object common {
  type Amount = BigDecimal

  def today = Calendar.getInstance().getTime
}

import common._

case class Account(no: String, name: String, dateOfOpen: Option[Date] = today.some, dateOfClose: Option[Date])
