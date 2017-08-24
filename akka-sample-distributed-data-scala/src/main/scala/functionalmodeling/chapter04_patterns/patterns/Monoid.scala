package functionalmodeling.chapter04_patterns.patterns


trait Monoid[T] {
  def zero: T
  def op(t1: T, t2: T): T
}

object Monoid {

  def apply[T](implicit m: Monoid[T]) = m

  implicit val IntAdditionMonoid = new Monoid[Int] {
    override def zero = 0

    override def op(t1: Int, t2: Int) = t1 + t2
  }


  implicit val BigDecimalAdditionMonoid = new Monoid[BigDecimal] {
    override def zero = BigDecimal(0)

    override def op(t1: BigDecimal, t2: BigDecimal) = t1 + t2
  }

  implicit def MapMonoid[K, V: Monoid] = new Monoid[Map[K ,V]] {
    def zero = Map.empty[K, V]

    override def op(t1: Map[K, V], t2: Map[K, V]) = t2.foldLeft(t1) { (a, e) =>
      val (key, value) = e
      a.get(key).map { v => a + ((key, implicitly[Monoid[V]].op(v, value))) }
        .getOrElse(a + ((key, value)))
    }
  }

  final val zeroMoney: Money = Money(Monoid[Map[Currency, BigDecimal]].zero)

  implicit def MoneyAdditionMonoid = new Monoid[Money] {
    val m = implicitly[Monoid[Map[Currency, BigDecimal]]]

    override def zero = zeroMoney

    override def op(t1: Money, t2: Money) = Money(m.op(t1.m, t2.m))
  }

  object MoneyOrdering extends Ordering[Money] {
    def compare(a:Money, b:Money) = a.toBaseCurrency compare b.toBaseCurrency
  }

  import MoneyOrdering._
  import scala.math.Ordering

  implicit val MoneyCompareMonoid = new Monoid[Money] {
    def zero = zeroMoney
    def op(m1: Money, m2: Money) = if (gt(m1, m2)) m1 else m2
  }

  implicit def BalanceMonoid = new Monoid[Balance] {
    val m = implicitly[Monoid[Money]]

    override def zero = Balance()

    override def op(t1: Balance, t2: Balance) = Balance(m.op(t1.amount, t2.amount))
  }
}
