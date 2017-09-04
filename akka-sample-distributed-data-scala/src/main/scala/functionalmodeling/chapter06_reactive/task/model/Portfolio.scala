package functionalmodeling.chapter06_reactive
package task
package model

import java.util._

import common._

case class Balance(ins: Instrument, holding: Amount, marketValue: Amount)

sealed trait Portfolio {
  def accountNo: String
  def asOf: Date
  def items: Seq[Balance]
  def totalMarketValue: Amount = items.foldLeft(BigDecimal(0d)) { (acc, i) => acc + i.marketValue }
}

case class CustomerPortfolio(accountNo: String, asOf: Date, items: Seq[Balance]) extends Portfolio
