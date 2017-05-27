package monads

import cats.{~>, Monad => CatsMonads}
import cats.implicits._
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

sealed trait Event

sealed trait Statistics

object Statistics {

  case class StatisticsOps(stats: Statistics) {
    def update(event: Event) = stats
  }

  implicit def toOps(stats: Statistics): StatisticsOps = StatisticsOps(stats)

  trait StatisticsAlg[F[_]] {
    def update(event: Event): F[Statistics]
  }

  class UpdateStatistics[F[_]: CatsMonads](statAlg: StatisticsAlg[F]) {
    def update(event: Event): F[Statistics] = statAlg.update(event)
  }

  class OptionInterpreter(stats: Statistics) extends StatisticsAlg[Option] {
    override def update(event: Event) = Option(stats.update(event))
  }
  val result = new UpdateStatistics(new OptionInterpreter(new Statistics {})).update(new Event {})
}

