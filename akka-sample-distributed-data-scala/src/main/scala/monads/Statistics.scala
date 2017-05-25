package monads


/**
  * 统计的Monad
  */
/*sealed trait Statistics[A] {

  val value: A

  def merge(event: LiveEventHBase): Statistics[A]

  def map[B](f: A => B): Statistics[B] = unit(f(value))

  def unit(a: A): Statistics[A]
}

object Statistics {

  case class LiveStatistics(value: LiveStatsHBase) extends Statistics[LiveStatsHBase] {

    override def unit(a: LiveStatsHBase) = LiveStatistics(a)

    override def merge[B](event: LiveEventHBase): Statistics[LiveStatistics] =
      map(event.updateStatistics(_))
  }
}*/
