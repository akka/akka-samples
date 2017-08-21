package functionalmodeling.chapter04_patterns

import java.util.Date

/**
  *
  */
/*
object Patterns {

  trait Monoid[A] {
    def zero: A
    def op(t1: A, t2: A): A
  }

  trait Foldable[F[_]] {
    def foldl[A, B](as: F[A], z: B, f: (B, A) => B): B

    def foldMap[A, B](as: F[A])(f: A => B)(implicit m: Monoid[B]): B =
      foldl(as, m.zero, (b: B, a: A) => m.op(b, f(a)))
  }

  def mapReduce[F[_], A, B](as: F[A])(f: A => B)(implicit fd: Foldable[F], m: Monoid[B]) = fd.foldMap(as)(f)

  sealed trait TransactionType
  case object DR extends TransactionType
  case object CR extends TransactionType

  sealed trait Currency
  case object USD extends Currency
  case object JPY extends Currency
  case object AUD extends Currency
  case object INR extends Currency

  case class Money(m: Map[Currency, BigDecimal]) {
    def toBaseCurrency: BigDecimal = ???
  }

  case class Transaction(txid: String, accountNo: String, date: Date,
                         amount: Money, txnType: TransactionType, status: Boolean)

  case class Balance(b: Money)

  trait Analytics[Transaction, Balance, Money] {
    def maxDebitOnDay(txns: List[Transaction])(implicit m: Monoid[Money]): Money

    def sumBalance(bs: List[Balance])(implicit m: Monoid[Money]): Money
  }

  //final val zeroMoney: Money = Money(Monoid[Map[Currency, BigDecimal]].zero)

  implicit def MonoidAdditionMonoid = new Monoid[Money] {
    val m = implicitly[Monoid[Map[Currency, BigDecimal]]]

    override def zero = Money(Map.empty[Currency, BigDecimal])

    override def op(t1: Money, t2: Money) = Money(m.op(t1.m, t2.m))
  }

  object Analytics extends Analytics[Transaction, Balance, Money] {
    /*override def maxDebitOnDay(txns: List[Transaction])(implicit m: Monoid[Money]): Money = {
      txns.filter(_.txnType == DR).foldLeft(m.zero) { (a, txn) =>
        m.op(a, valueOf(txn))
      }
    }*/
    override def maxDebitOnDay(txns: List[Transaction])(implicit m: Monoid[Money]) =
      mapReduce(txns.filter(_.txnType == DR))(valueOf)

    override def sumBalance(bs: List[Balance])(implicit m: Monoid[Money]) =
      mapReduce(bs)(creditBalance)
    /*override def sumBalance(bs: List[Balance])(implicit m: Monoid[Money]) =
      bs.foldLeft(m.zero) { (a, b) => m.op(a, creditBalance(b)) }
*/
    private def valueOf(txn: Transaction): Money = txn.amount
    private def creditBalance(b: Balance): Money = b.b

    private def gt(m1: Money, m2: Money): Boolean = ???


  }
}
*/
