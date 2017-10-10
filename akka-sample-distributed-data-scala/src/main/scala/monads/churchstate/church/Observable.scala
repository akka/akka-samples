package monads.churchstate.church

/**
  *
  */
sealed trait Observable[A] {
  import Observable._

  def next(receiver: Receiver[A]): Unit

  def foldLeft[B](zero: B)(f: (A, B) => B): B = {
    var result = zero
    val receiver = new StatefulReceiver[A]()

    next(receiver)

    while(!receiver.isEmpty) {
      result = f(receiver.get, result)
      next(receiver)
    }

    result
  }
}

object Observable {
  def fromStream[A](stream: Stream[A]): Observable[A] = stream match {
    case Stream.Zip(l, r) => Zip(fromStream(l), fromStream(r))
    case Stream.Map(s, f) => Map(fromStream(s), f)
    case Stream.FromIterator(s) => FromIterator(s)
  }

  def always[A](elem: A): Observable[A] = FromIterator(Iterator.continually(elem))

  def apply[A](a: A*): Observable[A] = FromIterator(Iterator(a: _*))

  final case class Map[A, B](source: Observable[A], f: A => B) extends Observable[B] {
    override def next(receiver: Receiver[B]): Unit = {
      val receiverA = new Receiver[A] {
        override def none: Unit = receiver.none

        override def some(a: A): Unit = {
          receiver.some((f(a)))
        }
      }

      source.next(receiverA)
    }
  }

  final case class Zip[A, B](left: Observable[A], right: Observable[B]) extends Observable[(A, B)] {
    override def next(receiver: Receiver[(A, B)]): Unit = {
      val receiverLeft= new Receiver[A] {
        override def none: Unit = receiver.none

        override def some(a: A): Unit = {
          val receiverRight = new Receiver[B] {
            override def none: Unit = receiver.none

            override def some(b: B): Unit = receiver.some((a, b))
          }

          right.next(receiverRight)
        }
      }

      left.next(receiverLeft)
    }
  }

  final case class FromIterator[A](source: Iterator[A]) extends Observable[A] {
    override def next(receiver: Receiver[A]): Unit = {
      if (source.hasNext) receiver.some(source.next()) else receiver.none
    }
  }
}