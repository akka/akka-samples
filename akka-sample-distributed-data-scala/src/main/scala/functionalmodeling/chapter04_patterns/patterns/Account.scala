package functionalmodeling.chapter04_patterns.patterns

import scala.util._
import java.util.Calendar



/**
  * 
  */

sealed trait Currency
case object USD extends Currency
case object JPY extends Currency
case object AUD extends Currency
case object INR extends Currency

object common {
  type Amount = BigDecimal

  val today = Calendar.getInstance.getTime
}

import common._

case class Money(m: Map[Currency, Amount]) {
  def toBaseCurrency: Amount = ???
}

case class Balance(amount: Money = Money(Map.empty[Currency, Amount]))

trait Account {

}

trait AccountRepository {
  def query(no: String): Try[Option[Account]]
}

final class Generator(rep: AccountRepository) {
  val no: String = scala.util.Random.nextString(10)

  def exists: Boolean = rep.query(no) match {
    case Success(Some(a)) => true
    case _ => false
  }
}