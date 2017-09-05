package functionalmodeling.chapter08_persistence
package cqrs
package pure

import scalaz._
import Scalaz._

import common._

import java.util.Date

trait Event[A] {
  def at: Date
}

case class Opened(no: String, name: String, openingDate: Option[Date], at: Date = today) extends Event[String]
case class Closed(no: String, closeDate: Option[Date], at: Date = today) extends Event[Unit]
case class Debited(no: String, amount: Amount, at: Date = today) extends Event[Unit]
case class Credited(no: String, amount: Amount, at: Date = today) extends Event[Unit]

object Event {
  private def debitImpl(a: Account, amount: Amount) = {
    if (a.balance.amount < amount) throw new RuntimeException("insufficient fund to debit")
    else a.copy(balance = Balance(a.balance.amount - amount))
  }

  private def creditImpl(a: Account, amount: Amount) = {
    a.copy(balance = Balance(a.balance.amount + amount))
  }

  def updateState(e: Event[_], initial: Map[String, Account]): Map[String, Account] = e match {
    case Opened(no, name, odate, at) =>
      initial + (no -> Account(no, name, odate.get))

    case Closed(no, cdate, at) =>
      initial + (no -> (initial(no).copy(dateOfClosing = cdate)))

    case Debited(no, amount, at) =>
      initial + (no -> debitImpl(initial(no), amount))

    case Credited(no, amount, at) =>
      initial + (no -> creditImpl(initial(no), amount))
  }
}

trait Commands {
  import Event._
  import scala.language.implicitConversions

  type Command[A] = Free[Event, A]

  private implicit def liftEvent[A](event: Event[A]): Command[A] = Free.liftF(event)

  def open(no: String, name: String, openingDate: Option[Date]): Command[String] = Opened(no, name, openingDate)
  def close(no: String, closeDate: Option[Date]): Command[Unit] = Closed(no, closeDate)
  def debit(no: String, amount: Amount): Command[Unit] = Debited(no, amount)
  def credit(no: String, amount: Amount): Command[Unit] = Credited(no, amount)

}

object Commands extends Commands

object Scripts extends Commands {

  def transfer(from: String, to: String, amount: Amount): Command[Unit] = for {
    _ <- debit(from, amount)
    _ <- credit(to, amount)
  } yield ()

  val composite = for {
    n <- open("a-123", "James LeBrons", Some(today))
    _ <- credit(n, 10000)
    _ <- credit(n, 20000)
    _ <- debit(n, 23000)
  } yield (())

  val compositeFail = for {
    n <- open("f-123", "James LeBrons", Some(today))
    _ <- credit(n, 10000)
    _ <- credit(n, 20000)
    _ <- debit(n, 50000)
  } yield (())

}

object PureInterpreter {
  import Event._
  import Commands.Command

  type MapState[A] = State[Map[String, Account], A]

  val step = new (Event ~> MapState) {
    override def apply[A](fa: Event[A]) = fa match {
      case o @ Opened(no, _, _, _) =>
        State { s => (updateState(o, s), no)}

      case c @ Closed(_, _, _) =>
        State { s => (updateState(c, s), ())}

      case d @ Debited(_, _, _) =>
        State { s => (updateState(d, s), ())}

      case c @ Credited(_, _, _ ) =>
        State { s => (updateState(c, s), ())}
    }
  }

  def interpret[A](c: Command[A], state: Map[String, Account] = Map.empty[String, Account]): Map[String, Account] =
    c.foldMap(step).exec(state)
}

object PureApp {
  def main(args: Array[String]): Unit = {
    import Event._
    import Scripts._
    import PureInterpreter._

    val r = interpret(composite)
    println(r)

    val rt = interpret(transfer("a-123", "a-123", 2000), r)
    println(rt)
    
    val rf = interpret(compositeFail)
    println(rf)
  }
}