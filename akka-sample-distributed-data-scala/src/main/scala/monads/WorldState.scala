package monads

/**
  * https://james-iry.blogspot.ie/2007/11/monads-are-elephants-part-4.html
  */

object A {

  val odd: PartialFunction[Int, Boolean] = {
    case i: Int if i % 2 == 0 => true
  }

  val (name, age) = ("James", 30)
}

sealed trait WorldState {
  def nextState: WorldState
}

abstract class IOApplication_v1 {
  private class WorldStateImpl(id: BigInt) extends WorldState {
    override def nextState = new WorldStateImpl(id + 1)
  }

  final def main(args: Array[String]): Unit = {
    iomain(args, new WorldStateImpl(0))
  }

  def iomain(args: Array[String], startState: WorldState): (WorldState, _)

}

abstract class IOApplication_v2 {
  private class WorldStateImpl(id: BigInt) extends WorldState {
    override def nextState = new WorldStateImpl(id + 1)
  }

  final def main(args: Array[String]): Unit = {
    val ioAction = iomain(args)
    ioAction(new WorldStateImpl(0))
  }

  def iomain(args: Array[String]): WorldState => (WorldState, _)
}

class HelloWorld_v1 extends IOApplication_v1 {
  import RTConsole_v1._

  override def iomain(args: Array[String], startState: WorldState) = putString(startState, "Hello World")
}

class HelloWorld_v2 extends IOApplication_v2 {
  import RTConsole_v2._

  override def iomain(args: Array[String]) = putString("Hello world")
}

class HelloWorld_v3 extends IOApplication_v3 {
  import RTConsole_v3._

  override def iomain(args: Array[String]) = putString("Hello　world")
}
class Evil_v1 extends IOApplication_v1 {
  import RTConsole_v1._

  override def iomain(args: Array[String], startState: WorldState) = {
    val (stateA, a) = getString(startState)
    val (stateB, b) = getString(startState)

    assert(a == b)
    (startState, b)
  }
}

class Evil_v2 extends IOApplication_v2 {
  import RTConsole_v2._

  override def iomain(args: Array[String]) = {
    { startState: WorldState =>
      val (statea, a) = getString(startState)
      val (stateb, b) = getString(startState)

      assert(a == b)
      (stateb, b)
    }
  }
}

abstract class IOApplication_v3 {
  private class WorldStateImpl(id: BigInt) extends WorldState {
    override def nextState = new WorldStateImpl(id + 1)
  }

  final def main(args: Array[String]): Unit = {
    val ioAction = iomain(args)
    ioAction(new WorldStateImpl(0))
  }

  def iomain(args: Array[String]): IOAction_v3[_]
}

object RTConsole_v1 {
  def getString(state: WorldState) = (state.nextState, Console.readLine())

  def putString(state: WorldState, s: String) = (state.nextState, Console.print(s))
}

object RTConsole_v2 {
  def getString = { state: WorldState  =>
    (state.nextState, Console.readLine)
  }

  def putString(s: String) = { state: WorldState =>
    (state.nextState, Console.print(s))
  }
}

object RTConsole_v3 {
  def getString = IOAction_v3(Console.readLine)

  def putString(s: String) = IOAction_v3(Console.print(s))
}
