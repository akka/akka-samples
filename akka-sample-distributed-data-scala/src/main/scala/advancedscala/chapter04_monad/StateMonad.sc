import cats.data.State

import scala.util.Try

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

/*def eval(op: String)(ophands: List[Int]): Int = op match {
  case "*" => ophands.head * ophands.tail.head
  case "+" => ophands.head + ophands.tail.head
  case _ => op.toInt
}*/
def operand(num: Int): CalcState[Int] = State[List[Int], Int] { stack =>
  (num :: stack, num)
}

def operator(func: (Int, Int) => Int): CalcState[Int] = State[List[Int], Int] { //oldStack =>
  /*lazy val (op1, op2, other) = (oldStack.tail.head, oldStack.head, oldStack.tail.tail)
  val result = func(op1, op2)
  (result :: other, result)*/
  //oldStack match {
    case a :: b :: tail =>
      val ans = func(a, b)
      (ans :: tail, ans)
    case _ =>
      sys.error("Fail")
  //}
}
def evalOne(sym: String): CalcState[Int] =
  sym match {
    case "*" =>
      operator(_ * _)
    case "+" =>
      operator(_ + _)
    case "-" =>
      operator(_ - _)
    case "/" =>
      operator(_ / _)
    case num =>
      operand(num.toInt)
  }


evalOne("42").run(Nil).value

val p1 = for {
  _ <- evalOne("1")
  _ <- evalOne("2")
  _ <- evalOne("+")
  _ <- evalOne("3")
  _ <- evalOne("*")
  _ <- evalOne("36")
  _ <- evalOne("/")
  _ <- evalOne("6")
  _ <- evalOne("-")
  _ <- evalOne("3")
  //_ <- evalOne("+")
  r <- evalOne("*")
} yield r
p1.run(Nil).value

import cats.syntax.applicative._

def evalAll(input: List[String]): CalcState[Int] =
  input.foldLeft(0.pure[CalcState]) { (a, b) =>
    a.flatMap(_ => evalOne(b))
  }

val p2 = evalAll(List("1", "2", "+", "3", "*"))
p2.run(Nil).value
0.pure[CalcState]//.run(Nil).value
1.pure[CalcState].run(Nil).value

/*
def evalAll2(input: List[String]): CalcState[Int] = input match {
  case Nil => 1.pure[CalcState]
  case h :: t =>
    for {
      _ <- evalOne(h)
      r <- evalAll2(t)
    } yield r
}

val p3 = evalAll2(List("1", "2", "+"))
p3.run(Nil).value

*/

val p5 = for {
  _ <- evalAll(List("1", "2", "*"))
  _ <- evalOne("3")
  _ <- evalAll(List("3", "5", "+"))
  ans <- evalOne("*")
} yield ans
p5.run(Nil).value
