package monix_tut

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, KillSwitches}
import monix.eval.Task
import monix.execution.cancelables.BooleanCancelable
import monix.reactive.{Consumer, Observable}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

/**
  * https://blog.scalac.io/2017/06/01/why-should-you-care-about-monix.html
  */
object MimicPersistKafkaMsg {

  sealed trait Done
  case object Done extends Done

  // BEGIN CONTEXT
  // Mimics deserialized message from Kafka source
  sealed trait DeserializedMsg

  case class MsgA(a: Long) extends DeserializedMsg
  case class MsgB(b:Long) extends DeserializedMsg

  trait Commitable {
    def commitOffset(implicit ec: ExecutionContext): Future[Done]
  }

  case class CommitableOffset(offset: Long) extends Commitable {
    override def commitOffset(implicit ec: ExecutionContext): Future[Done] = Future {
      Thread.sleep(scala.util.Random.nextLong().abs % 10)
      Done
    }
  }

  case class CommitableMsg[T](payload: T, commitable: Commitable)

  def someBusinessTask(a: Long) = Task {
    println(s"Task doing business with $a")
    a
  }

  def someBusinessFuture(a: Long)(implicit ec: ExecutionContext): Future[Long] = Future {
    println(s"Future doing business with $a")
    a
  }

  def makeCommitableMessage(i: Long): CommitableMsg[DeserializedMsg] =
    CommitableMsg(makeMessage(i), CommitableOffset(i))

  def makeMessage(i: Long) = {
    if (i % 2 == 0)
      MsgA(i)
    else MsgB(i)

  }

  def main(args: Array[String]): Unit = {
    val killSwith = KillSwitches.shared("injectable switch")
    val injectedCancelable: BooleanCancelable = BooleanCancelable()

    implicit val ec = scala.concurrent.ExecutionContext.global

    implicit val actorSystem = ActorSystem("test-1")
    implicit val mat = ActorMaterializer()

    val akkaSource = Source
      .tick(0.millis, 10.millis, 1L)
      .scan(0L)(_ + _)
      .map(makeCommitableMessage)

    val akkaRun: Future[akka.Done] = akkaSource
      .mapAsync(1) {
        case CommitableMsg(MsgA(a), offset) =>
          someBusinessFuture(a).map(_ => offset)

        case CommitableMsg(_, offset) =>
          Future.successful(offset)
      }
      .groupedWithin(100, 1.seconds)
      .filter(_.nonEmpty)
      .mapAsync(1)(_.last.commitOffset)
      .via(killSwith.flow)
      .runWith(Sink.ignore)
    
    println("I do _have_ some doubts about `Observable.intervalAtFixedRate` because of Monix stream falls behind.")

    val monixObservable = Observable
      .intervalAtFixedRate(100.millis)
      .map(makeCommitableMessage)

    val monixRun = monixObservable
      .mapAsync(1) {
        case CommitableMsg(MsgA(a), offset) =>
          someBusinessTask(a).map(_ => offset)

        case CommitableMsg(_, offset) =>
          Task.now(offset)
      }
      .bufferTimedAndCounted(1.second, 10)
      .filter(_.nonEmpty)
      .mapAsync(1)(offsets => Task.fromFuture(offsets.last.commitOffset))
      .takeWhileNotCanceled(injectedCancelable)
      .consumeWith(Consumer.complete)
      .runAsync(monix.execution.Scheduler.global)

    Thread.sleep(10000)
    killSwith.shutdown()
    injectedCancelable.cancel()
    mat.shutdown()
    actorSystem.terminate()

  }
}
