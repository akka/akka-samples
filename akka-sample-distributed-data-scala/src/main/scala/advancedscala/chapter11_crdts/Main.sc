

final case class GCounterOriginial(counters: Map[String, Int]) {
  def increment(machine: String, amount: Int) = {
    val inc = counters.get(machine) match {
      case Some(num) => num + amount
      case None => amount
    }

    GCounterOriginial(counters + (machine -> inc))
  }

  def get: Int = counters.values.sum

  def merge(that: GCounterOriginial): GCounterOriginial = {
    /*counters.foldRight(that) { case ((machine, amount), counter) =>
      counter.counters.get(machine) match {
        case Some(n) =>
          GCounter(counter.counters + (machine -> (amount max n)))
        case None =>
          GCounter(counter.counters + (machine -> amount))
      }
    }*/
    GCounterOriginial(
      that.counters ++  {
        for ((k, v) <- counters) yield {
          k -> (v max (that.counters.getOrElse(k, 0)))
        }
      }
    )
  }
}

import cats.{Foldable, Monoid}
trait BoundedSemiLattice[A] extends Monoid[A] {
  def combine(x: A, y: A): A
  def empty: A
}

object BoundedSemiLattice {
  implicit object IntLattice extends BoundedSemiLattice[Int] {
    def combine(x: Int, y: Int) = x max y
    def empty: Int = 0
  }

  implicit def setBoundedSemiLattice[A]: BoundedSemiLattice[Set[A]] =
    new BoundedSemiLattice[Set[A]] {
      def combine(x: Set[A], y: Set[A]): Set[A] = x union(y)
      def empty: Set[A] = Set.empty[A]
    }
}

import cats.syntax.semigroup._
import cats.syntax.foldable._
import cats.instances.map._

final case class GCounter2[A](counters: Map[String, A]) {

  def increment(machine: String, amount: A)
               (implicit m: Monoid[A]): GCounter2[A] =
    GCounter2(counters + (machine -> (amount |+| counters.getOrElse(machine, m.empty))))

  def get(implicit m: Monoid[A]): A = this.counters.foldMap(identity)

  def merge(that: GCounter2[A])(implicit m: BoundedSemiLattice[A]): GCounter2[A] =
    GCounter2(
      this.counters |+| (that.counters)
    )
}

import scala.language.higherKinds

trait GCounter[F[_, _], K, V] {
  def increment(f: F[K, V])(k: K, v: V)
               (implicit m: Monoid[V]): F[K, V]

  def total(f: F[K, V])(implicit m: Monoid[V]): V

  def merge(f1: F[K, V], f2: F[K, V])
           (implicit b: BoundedSemiLattice[V]): Map[K, V]

}

object GCounter {
  implicit def mapGCounterInstance[K, V]: GCounter[Map, K, V] = {
    new GCounter[Map, K, V] {
      import cats.instances.map._

      override def increment(f: Map[K, V])(k: K, v: V)(implicit m: Monoid[V]) =
        f + (k -> (f.getOrElse(k, m.empty) |+| v))

      override def total(f: Map[K, V])(implicit m: Monoid[V]) =
        f.foldMap(identity)

      override def merge(f1: Map[K, V], f2: Map[K, V])(implicit b: BoundedSemiLattice[V]) =
        f1 |+| f2


    }
  }

  def apply[F[_, _], K, V](implicit g: GCounter[F, K, V]) = g

}

import cats.instances.int._
import GCounter._

val g1 = Map("a" -> 7, "b" -> 3)
val g2 = Map("a" -> 5, "b" -> 5)


println(s"Merged: ${GCounter[Map, String, Int].merge(g1, g2)}")
println(s"Total: ${GCounter[Map, String, Int].total(g1)}")

trait KeyValueStore[F[_, _]] {
  def +[K, V](f: F[K, V])(key: K, value: V): F[K, V]

  def get[K, V](f: F[K, V])(key: K): Option[V]

  def getOrElse[K, V](f: F[K, V])(key: K, default: V): V =
    get(f)(key).getOrElse(default)

}

object KeyValueStore {
  implicit class KeyValueStoreOps[F[_, _], K, V](f: F[K, V]) {
    def +(key: K, value: V)(implicit kv: KeyValueStore[F]): F[K, V] =
      kv.+(f)(key, value)

    def get(key: K)(implicit kv: KeyValueStore[F]): Option[V] =
      kv.get(f)(key)

    def getOrElse(key: K, default: V)(implicit kv: KeyValueStore[F]): V=
      kv.getOrElse(f)(key, default)
  }

  implicit object mapKeyValueStoreInstance extends KeyValueStore[Map] {
    override def +[K, V](f: Map[K, V])(key: K, value: V): Map[K, V] =
      f + (key -> value)

    override def get[K, V](f: Map[K, V])(key: K): Option[V] = f.get(key)

    override def getOrElse[K, V](f: Map[K, V])(key: K, default: V): V =
      f.getOrElse(key, default)
  }

  implicit def keyValueInstance[F[_,_], K, V](
                                               implicit
                                               k: KeyValueStore[F],
                                               km: Monoid[F[K, V]],
                                               kf: Foldable[({type l[A] = F[K, A]})#l]
                                             ): GCounter[F, K, V] =
    new GCounter[F, K, V] {
      import KeyValueStore._
      override def increment(f: F[K, V])(k: K, v: V)(implicit m: Monoid[V]) =
        f + (k, (f.getOrElse(k, m.empty) |+| v))

      override def total(f: F[K, V])(implicit m: Monoid[V]) =
        f.foldMap(identity _)

      override def merge(f1: F[K, V], f2: F[K, V])(implicit b: BoundedSemiLattice[V]): F[K, V] =
        f1 |+| f2
    }

}


