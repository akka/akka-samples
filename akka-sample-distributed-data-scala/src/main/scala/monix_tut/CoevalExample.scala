package monix_tut

import monix.eval.Coeval

import scala.io.StdIn
import scala.util.control.NonFatal

/**
  * https://blog.scalac.io/2017/06/01/why-should-you-care-about-monix.html
  */
object CoevalExample {

  def main(args: Array[String]): Unit = {
    /**
      * Coeval[A] can be seen also as `Function0[A]`
      */
    val prompt: Coeval[Unit] = Coeval.eval(println("Please enter a number"))
    val lastPrompt: Coeval[Unit] = Coeval.eval(println(s"Please enter a number, otherwise 42 will be used"))
    val readLine: Coeval[String] = Coeval.eval(StdIn.readLine())

    def promptForNumber(prompt: Coeval[Unit]): Coeval[Int] = for {
      _ <- prompt
      s <- readLine
    } yield s.toInt

    val num = promptForNumber(prompt)
      .onErrorRestart(2)
      .onErrorFallbackTo(promptForNumber(lastPrompt))
      .onErrorRecover {
        case NonFatal(e) =>
          println(e)
          42
      }.memoize // Save result after first execution

    println(s"${num.value}")  // First call, with side effects and actual computations
    println(s"${num.value}")  // Returns memoized result
  }

}
