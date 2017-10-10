package monads

/**
  * https://translate.google.com.hk/?rlz=1C1CHRY_enCN517CN520&ie=UTF-8&hl=zh-CN&tab=wT
  */
object ChurchState {

}

class Calculator {
  def literal(v: Double): Double = v
  def add(a: Double, b: Double): Double = a + b
  def subtract(a: Double, b: Double): Double = a - b
  def multiply(a: Double, b: Double): Double = a * b
  def divide(a: Double, b: Double): Double = a / b
}

class TrigCalculator extends Calculator {
  def sin(v: Double): Double = Math.sin(v)
  def cos(v: Double): Double = Math.cos(v)
}

sealed trait Calculation
final case class Literal(v: Double) extends Calculation
final case class Add(a: Calculation, b: Calculation) extends Calculation
final case class Subtract(a: Calculation, b: Calculation) extends Calculation
final case class Multiply(a: Calculation, b: Calculation) extends Calculation
final case class Divide(a: Calculation, b: Calculation) extends Calculation

object Calculation {
  def eval(c: Calculation): Double =
    c match {
      case Literal(v)     => v
      case Add(a, b)      => eval(a) + eval(b)
      case Subtract(a, b) => eval(a) - eval(b)
      case Multiply(a, b) => eval(a) * eval(b)
      case Divide(a, b)   => eval(a) / eval(b)
    }

  def prettyPrint(c: Calculation): String =
    c match {
      case Literal(v)     => v.toString
      case Add(a, b)      => s"${prettyPrint(a)} + ${prettyPrint(b)}"
      case Subtract(a, b) => s"${prettyPrint(a)} - ${prettyPrint(b)}"
      case Multiply(a, b) => s"${prettyPrint(a)} * ${prettyPrint(b)}"
      case Divide(a, b)   => s"${prettyPrint(a)} / ${prettyPrint(b)}"
    }
}