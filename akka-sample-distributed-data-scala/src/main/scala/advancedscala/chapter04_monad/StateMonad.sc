import cats.data.State

val a = State[Int, String] { state =>
  (state, s"The state is $state")
}

val (state, result) = a.run(10).value
a.run(10)

val state1 = a.runS(100).value

a.runS(100)
val result1 = a.runA(20).value

val step1 = State[Int, String] { num =>
  val ans = num + 1
  (ans, s"Result of step1: $ans")
}

val step2 = State[Int, String] { num =>
  val ans = num * 2
  (ans, s"Result of step2: $ans")
}

val both = for {
  a <- step1
  b <- step2
} yield (a, b)

val (state2, result2) = both.run(10).value

val getDemo = State.get[Int]
getDemo.run(20).value
getDemo.run(20)

val setDemo = State.set[Int](30)
setDemo.run(10).value

val pureDemo = State.pure[Int, String]("Result")
pureDemo.run(5).value

val inspectDemo = State.inspect[Int, String](_ + "!")
inspectDemo.run(3).value

val modifyDemo = State.modify[Int](_ + 2)
modifyDemo.run(30).value

val r3 = for {
  g <- getDemo
  _ <- setDemo
  b <- pureDemo
  _ <- modifyDemo
  i <- inspectDemo
} yield (g, b, i)
r3.run(20).value

import State._
val program: State[Int, (Int, Int, Int)] = for {
  a <- get[Int]
  _ <- set[Int](a + 1)
  b <- get[Int]
  _ <- modify[Int](_ * 2)
  c <- inspect[Int, Int](_ * 100)
} yield (a, b, c)

val (sp, rp) = program.run(2).value

// Exercise
type CalcState[A] = State[List[Int], A]

def eval(op: String)(ophands: List[Int]): Int = op match {
  case "*" => ophands.head * ophands.tail.head
  case "+" => ophands.head + ophands.tail.head
  case _ => op.toInt
}
def evalOne(sym: String): CalcState[Int] = State[List[Int], Int] { oldStack =>
  val newStack = eval(sym)(oldStack) :: oldStack
  val result = eval(sym)(oldStack)
  (newStack, result)
}

evalOne("42").run(Nil).value

val p1 = for {
  _ <- evalOne("1")
  _ <- evalOne("2")
  ans <- evalOne("+")
} yield ans
p1.runA(Nil).value
