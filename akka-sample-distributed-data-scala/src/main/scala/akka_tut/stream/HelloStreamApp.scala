package akka_tut.stream

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{Balance, Broadcast, Flow, GraphDSL, Keep, Merge, RunnableGraph, Sink, Source}

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
  * Created by liaoshifu on 17/11/3
  *
  * https://jobs.zalando.com/tech/blog/about-akka-streams/?gh_src=4n3gxh1
  */
object HelloStreamApp {

  def main(args: Array[String]): Unit = {

    val helloSource = Source.single("Hello World")
      .map(s => s.toUpperCase())
    val helloWorldStream: RunnableGraph[NotUsed] =
      helloSource
      .to(Sink.foreach(println))

    implicit val system = ActorSystem("akka-streams-example")
    implicit val materializer = ActorMaterializer()
    implicit val ec = system.dispatcher

    val materializeValue = helloWorldStream.run()
    println(materializeValue)

    /*val helloWorldStream2: RunnableGraph[Future[Done]] =
      helloSource
      .toMat(Sink.foreach(println))(Keep.right)

    val doneF: Future[Done] = helloWorldStream2.run()

    doneF.onComplete {
      case Success(Done) =>
        println("Stream finished successfully")
      case Failure(e) =>
        println(s"Stream failed with $e")
    }

    val helloWorldStream3/*: RunnableGraph[(UniqueKillSwitch, Future[Done])]*/ =
      helloSource
      .viaMat(KillSwitches.single)(Keep.right)
      .toMat(Sink.foreach(println))(Keep.both)

    val (killSwitch, doneF2): (UniqueKillSwitch, Future[Done]) = helloWorldStream3.run()
    killSwitch.shutdown() // or killSwitch.abort(new Exception("Exception from KillSwitch))

    val complex = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val A: Outlet[Int] = builder.add(Source.single(0)).out
      val B: UniformFanOutShape[Int, Int] = builder.add(Broadcast[Int](2))
      val C: UniformFanInShape[Int, Int] = builder.add(Merge[Int](2))
      val D: FlowShape[Int, Int] = builder.add(Flow[Int].map(_ + 1))
      val E: UniformFanOutShape[Int, Int] = builder.add(Balance[Int](2))
      val F: UniformFanInShape[Int, Int] = builder.add(Merge[Int](2))
      val G: Inlet[Any] = builder.add(Sink.foreach(println)).in
                C <~ F
      A ~> B ~> C ~> F
           B ~> D ~> E ~> F
                     E ~> G

      ClosedShape
    })

    complex.run()
    println(Thread.currentThread().getName)

    Source.single("Hello")
      .map(_ + " Stream World!")
      .to(Sink.foreach(s => println(Thread.currentThread().getName + " " + s)))
      .run()
    println("running")
*/
    /*val completion = Source.single("Hello Stream World!\n")
      .map { s => println(Thread.currentThread().getName + " " + s); s }
      .map { s => println(Thread.currentThread().getName + " " + s); s }
      .map { s => println(Thread.currentThread().getName + " " + s); s }
      .map { s => println(Thread.currentThread().getName + " " + s); s }
      .map { s => println(Thread.currentThread().getName + " " + s); s }
      .runWith(Sink.foreach(s => println(Thread.currentThread().getName + " " + s)))

    */

    def processingStage(name: String): Flow[String, String, NotUsed] =
      Flow[String].map { s =>
        println(s"$name started processing $s on thread ${Thread.currentThread().getName}")
        Thread.sleep(100)
        println(s"$name finished processing $s")
        s
      }

    val completion = Source(List("Hello", "Streams", "World!"))
      .via(processingStage("A")).async
      .via(processingStage("B")).async
      .via(processingStage("C")).async
      .via(processingStage("C")).async
      .via(processingStage("D")).async
      .runWith(Sink.foreach(s => println(s"Go output $s")))

    completion.onComplete(_ => system.terminate())

    //Thread.sleep(1000)
    //system.terminate()
  }
}
