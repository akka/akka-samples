package functionalmodeling.chapter08_persistence
package cqrs
package service

import functionalmodeling.chapter08_persistence.cqrs.lib.Aggregate
import org.joda.time.DateTime

import scalaz._
import Scalaz._

object common {
  type Amount = BigDecimal
  type Error = String

  def today = DateTime.now
}

import common._

case class Balance(amount: Amount = 0)

case class Account(no: String, name: String, dateOfOpening: DateTime = today, dateOfClosing: Option[DateTime] = None,
                   balance: Balance = Balance()) extends Aggregate {
  def id = no
  def isClosed = dateOfClosing.isDefined
}

object Account {
  implicit val showAccount: Show[Account] = Show.shows { a => a.toString }
}