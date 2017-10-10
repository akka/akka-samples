package monix_tut

import monix.eval.{MVar, Task}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util._

/**
  * https://blog.scalac.io/2017/06/01/why-should-you-care-about-monix.html
  */
object MVarExample {

  import monix.execution.Scheduler.Implicits.global

  def main(args: Array[String]): Unit = {
    val N = 100000
    val mvar = MVar.empty[Int]

    /**
      * Puts 'n', 'n+1', ..., 'N-1' to 'mvar'
      */
    def produce(n: Int): Task[Unit] = {
      println(s"produc($n)")
      if (n < N)
        mvar.put(n).flatMap {_ =>
          println(s"produced $n")
          produce(n + 1)
        }
      else
        Task.now(())
    }
    // Takes 'N-c' values from 'mvar' and sums them. Fails if cannot take in 100 ms.
    def consume(sum: Long, c: Int): Task[Long] =  {
      println(s"consume($sum, $c)")
      if(c < N) {
        // Fails if producer is not able to put value in approx. 100ms
        mvar.take
          .timeout(100.millisecond)
          .flatMap { v =>
            println(s"consumed $v")
            consume(v + sum, c + 1)
          }
      } else {
        println(s"add to Task...")
        Task.now(sum)
      }
    }

    val consumer = consume(0, 0).runAsync

    val producer = produce(0).runOnComplete {
      case Success(_) => println(s"Producer finished")
      case Failure(ex) => println(s"Producer failed: $ex")
    }

    consumer.onComplete {
      case Success(s) => println(s"Sum is $s")
      case Failure(ex) =>
        println(s"Consumer failed: $ex")
        producer.cancel()
        consumer.cancel()
    }

    Await.result(consumer, 60.seconds)
    println("Done")
  }
}
