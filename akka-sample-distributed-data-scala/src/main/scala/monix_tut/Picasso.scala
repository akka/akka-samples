package monix_tut

import monix.eval.Task
import monix.execution.{CancelableFuture, Scheduler}
import org.specs2.concurrent.TimeoutFailure

import scala.concurrent.{Await, TimeoutException}
import scala.concurrent.duration._
import scala.util.Random

/**
  * https://blog.scalac.io/2017/06/01/why-should-you-care-about-monix.html
  */
object Picasso {

  implicit val computationScheduler = Scheduler.computation()

  val ioScheduler = Scheduler.io()

  type Picture = String

  val maybePicassoImage =
    """
      |..\\....
      |...\\...
      |....\\..
      |....//..
      |...//...
      |..//....
    """

  sealed  trait AnalysisResult
  case object NotPicasso extends AnalysisResult
  case object Picasso extends AnalysisResult
  case object HardToSay extends AnalysisResult

  /**
    * Monix gives plethora of combinators but there is a place for one more
    */
  def retryWithDelay[A](t: Task[A], delay: FiniteDuration, restarts: Int) =
    t.onErrorFallbackTo(t.delayExecution(delay).onErrorRestart(restarts))

  def analyseLocally(pic: Picture): Task[AnalysisResult] =
    Task.now(HardToSay) // Mock, eagerly wraps HardToSay in Task, like Future.successful
    .executeOn(computationScheduler)

  def analyseRemote(pic: Picture): Task[AnalysisResult] =
    Task.eval(Picasso)        // Mock
    .delayResult(3.seconds)   // ... with artificial delay
    .executeOn(ioScheduler)

  def storeResult(pic: Picture, result: AnalysisResult): Task[Unit] =
    Task.eval(())
    .delayResult(Random.nextInt(5).seconds)
    .timeout(3.seconds)   // Task will fail if does not complete in 3 seconds
    .onErrorRecover {
      case ex: TimeoutException =>
        println(s"Logging severe error to bring human attention, $ex")
    }

  def analyseAndStoreResult(pic: Picture) =
    analyseLocally(pic).flatMap {
      case HardToSay =>
        retryWithDelay(analyseRemote(pic), 3.seconds, 5)

      case resolveResult: AnalysisResult =>
        Task.now(resolveResult)
    }.flatMap(storeResult(pic, _).executeOn(ioScheduler))

  def main(args: Array[String]): Unit = {
    val done: CancelableFuture[Unit] = analyseAndStoreResult(maybePicassoImage).runAsync
    
    done.foreach(_ => println("Done!"))

    Await.result(done, 30.seconds)
    
  }

}
