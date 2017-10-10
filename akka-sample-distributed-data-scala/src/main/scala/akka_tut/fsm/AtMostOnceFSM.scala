package akka_tut.fsm

import akka.actor.FSM

/**
  *
  */
class AtMostOnceFSM[S, D] extends FSM[S, D] {

  protected [this] def hasBeenProcessed(message: S): Boolean = false

  def atMostOnce(stateFunction: StateFunction): StateFunction = new StateFunction() {
    override def isDefinedAt(msg: Event): Boolean = stateFunction.isDefinedAt(msg)

    override def apply(msg: Event): State = msg match {
      case m @ Event(s: S, _) =>
        if (!hasBeenProcessed(s)) {
          stateFunction(m)
        } else stay()

      case m @ _ => stateFunction(m)
    }
  }
}
