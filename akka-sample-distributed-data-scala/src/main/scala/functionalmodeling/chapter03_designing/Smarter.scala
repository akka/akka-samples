package functionalmodeling.chapter03_designing

import java.util._

import scala.util._

/**
  *
  */
object Smarter {

  type Amount = BigDecimal
  def today = Calendar.getInstance().getTime

  case class Balance(amount: Amount = 0)

  sealed trait Account {
    def no: String
    def name: String
    def dateOfOpen: Option[Date]
    def dateOfClose: Option[Date]
    def balance: Balance
  }

  final case class CheckingAccount private (
                                           no: String,
                                           name: String,
                                           dateOfOpen: Option[Date],
                                           dateOfClose: Option[Date],
                                           balance: Balance
                                           ) extends Account

  final case class SavingsAccount private (
                                           no: String,
                                           name: String,
                                           rateOfInterest: Amount,
                                           dateOfOpen: Option[Date],
                                           dateOfClose: Option[Date],
                                           balance: Balance
                                         ) extends Account

  object Account {
    def checkingAccount(no: String, name: String, openDate: Option[Date],
                        closeDate: Option[Date], balance: Balance): Try[Account] = {
      closeDateCheck(openDate, closeDate).map { d =>
        CheckingAccount(no, name, Some(d._1), d._2, balance)
      }
    }

    def savingsAccount(no: String, name: String, rate: BigDecimal,
                       openDate: Option[Date], closeDate: Option[Date], balance: Balance
                      ): Try[Account] = {
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

  case class Lens[O, V](
                       get: O => V,
                       set: (O, V) => O
                       )

  case class Address(no: String, street: String, city: String, state: String, zip: String)
  case class Customer(id: Int, name: String, address: Address)

  val addressNoLens = Lens[Address, String](
    get = _.no,
    set = (o, v) => o.copy(no = v)
  )

  val custAddressLens = Lens[Customer, Address](
    get = _.address,
    set = (o, v) => o.copy(address = v)
  )

  def compose[Outer, Inner, Value](
                                  outer: Lens[Outer, Inner],
                                  inner: Lens[Inner, Value]
                                  ) = Lens[Outer, Value](
    get = outer.get andThen inner.get,
    set = (obj, value) => outer.set(obj, inner.set(outer.get(obj), value))
  )

  case class Wrap[T](value: T) extends AnyVal {
    def unwrap = value
  }

  import spire.math.Integral
  case class Natural private (value: Wrap[BigInt]) {
    def copy(value: Wrap[BigInt] = this.value): Option[Natural] =

      Natural(value.unwrap)
  }

  object Natural {
    def apply[A](x: A)(implicit A: Integral[A]): Option[Natural] =
      if (A.isSignPositive(x)) Some(Natural(Wrap(x.toString.toLong)))
      else None
  }
}
