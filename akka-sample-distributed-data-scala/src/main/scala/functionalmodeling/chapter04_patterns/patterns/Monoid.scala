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

  //final val zeroMoney: Money = Money(Monoid[Map[Currency, BigDecimal]].zero)

  /*implicit def MoneyAdditionMonoid = new Monoid[Money] {
    ov
  }*/
}
