package functionalmodeling.chapter06_reactive
package future
package model

import java.util._

import common._

sealed trait InstrumentType
case object CCY extends InstrumentType
case object EQ extends InstrumentType
case object FI extends InstrumentType

sealed trait Instrument {
  def instrumentType: InstrumentType
}

case class Equity(isin: String, name: String, issueDate: Date, faceValue: Amount) extends Instrument {
  final val instrumentType = EQ
}

case class FixedIncome(isin: String, name: String, issueDate: Date, maturityDate: Option[Date], instrumentType: InstrumentType) extends Instrument

case class Currency(isin: String, instrumentType: InstrumentType) extends Instrument