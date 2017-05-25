package monads

/**
  * https://james-iry.blogspot.ie/2007/11/monads-are-elephants-part-4.html
  */
sealed trait IOAction[+A] extends (WorldState => (WorldState, A))

private class SimpleAction[+A](
                              expression: => A
                              ) extends IOAction[A] {
  def apply(state: WorldState): (WorldState, A) = (state.nextState, expression)
}

sealed trait IOAction_v3[+A] extends (WorldState => (WorldState, A))

object IOAction_v3 {
  def apply[A](expression: => A): IOAction_v3[A] = new SimpleAction(expression)

  private class SimpleAction[+A](expression: => A) extends IOAction_v3[A] {
    def apply(state: WorldState) = (state.nextState, expression)
  }
}

sealed abstract class IOAction_v4[+A] extends (WorldState => (WorldState, A)) {
  def map[B](f: A => B): IOAction_v4[B] = flatMap { x => IOAction_v4(f(x)) }

  def flatMap[B](f: A => IOAction_v4[B]): IOAction_v4[B] =
    new ChainedAction(this, f)

  private class ChainedAction[+A, B](action1: IOAction_v4[B], f: B => IOAction_v4[A])
    extends IOAction_v4[A] {
    def apply(state1: WorldState) = {
      val (state2, intermediateResult) = action1(state1)
      val action2 = f(intermediateResult)

      action2(state2)
    }
  }
}

object IOAction_v4 {
  def apply[A](expression: => A): IOAction_v4[A] = new SimpleAction(expression)

  private class SimpleAction[+A](expression: => A) extends IOAction_v4[A] {
    override def apply(v1: WorldState) = (v1.nextState, expression)
  }
}