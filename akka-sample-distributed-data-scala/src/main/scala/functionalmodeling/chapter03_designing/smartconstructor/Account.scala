package functionalmodeling.chapter03_designing.smartconstructor

import java.util.{Calendar, Date}

import scala.util.{Failure, Success, Try}

/**
  * https://github.com/debasishg/frdomain/blob/master/src/main/scala/frdomain/ch3/smartconstructor/Account.scala
  */
object common {
  type Amount = BigDecimal

  def today = Calendar.getInstance.getTime
}

import functionalmodeling.chapter03_designing.smartconstructor.common._

case class Balance(amount: Amount = 0)

sealed trait Account {
  def no: String
  def name: String
  def dateOfOpen: Option[Date]
  def dateOfClose: Option[Date]
  def balance: Balance
}

/**
  * In order to ensure that the user cannot use `apply` and `copy` as well, we need
  * to delegate both of them to the smart constructor. Still there can be some hairy issues
  * as in http://stackoverflow.com/questions/19462598/scala-case-class-implementation-of-smart-constructors
  **/

final case class CheckingAccount private (no: String, name: String, dateOfOpen: Option[Date],
                                          dateOfClose: Option[Date] = None, balance: Balance = Balance()) extends Account {
  def copy(no: String = no,
           name: String = name,
           dateOfOpen: Option[Date] = dateOfOpen,
           dateOfClose: Option[Date] = dateOfClose,
           balance: Balance = balance) = Account.checkingAccount(no, name, dateOfOpen, dateOfClose, balance)

  def apply(no: String = no,
           name: String = name,
           dateOfOpen: Option[Date] = dateOfOpen,
           dateOfClose: Option[Date] = dateOfClose,
           balance: Balance = balance) = Account.checkingAccount(no, name, dateOfOpen, dateOfClose, balance)
}

final case class SavingsAccount private (no: String, name: String, rateOfInterest: Amount,
                                         dateOfOpen: Option[Date], dateOfClose: Option[Date] = None, balance: Balance = Balance()
                                        ) extends Account {
  def copy(no: String = no,
           name: String = name,
           rateOfInterest: Amount = rateOfInterest,
           dateOfOpen: Option[Date] = dateOfOpen,
           dateOfClose: Option[Date] = dateOfClose,
           balance: Balance = balance) = Account.savingsAccount(no, name, rateOfInterest, dateOfOpen, dateOfClose, balance)

  def apply(no: String = no,
           name: String = name,
           rateOfInterest: Amount = rateOfInterest,
           dateOfOpen: Option[Date] = dateOfOpen,
           dateOfClose: Option[Date] = dateOfClose,
           balance: Balance = balance) = Account.savingsAccount(no, name, rateOfInterest, dateOfOpen, dateOfClose, balance)

}

object Account {
  def checkingAccount(no: String, name: String, openDate: Option[Date], closeDate: Option[Date], balance: Balance): Try[Account] = {
    closeDateCheck(openDate, closeDate).map { d =>
      CheckingAccount(no, name, Some(d._1), d._2, balance)
    }
  }

  def savingsAccount(no: String, name: String, rate: BigDecimal,
                     openDate: Option[Date], closeDate: Option[Date], balance: Balance
                    ): Try[Account] = {
    println("in smart")
    closeDateCheck(openDate, closeDate).map { d =>
      if (rate <= BigDecimal(0))
        throw new Exception(s"Interest rate $rate must be > 0")
      else SavingsAccount(no, name, rate, Some(d._1), d._2, balance)
    }
  }

  private def closeDateCheck(openDate: Option[Date], closeDate: Option[Date]): Try[(Date, Option[Date])] = {
    val od = openDate.getOrElse(today)

    closeDate.map { cd =>
      if (cd before(od)) Failure(new Exception(
        s"Close date [$cd] cannot be earlier than open date [$od]"
      )) else Success((od, Some(cd)))
    }.getOrElse(Success((od, closeDate)))
  }
}
