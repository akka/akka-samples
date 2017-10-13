package akka_tut.stream

import akka._
import akka.actor._
import akka.stream._
import akka.stream.scaladsl._
import akka.util._
import akka.stream.stage._

import scala.util.{Failure, Success}

/**
  * 2017-10-13
  * https://stackoverflow.com/questions/35120082/how-to-get-started-with-akka-streams
  */
object ComplexTcpServer {

  def main(args: Array[String]): Unit = {

    val closeConnection = new GraphStage[FlowShape[String, String]] {
      val in = Inlet[String]("closeConnection.in")
      val out = Outlet[String]("closeConnection.out")

      override def shape = FlowShape(in, out)

      override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
        setHandler(in, new InHandler {
          override def onPush(): Unit = grab(in) match {
            case "q" =>
              push(out, "BYE")
              completeStage()

            case msg =>
              push(out, s"Server hereby respond to message: $msg\n")
          }
        })

        setHandler(out, new OutHandler {
          override def onPull(): Unit = pull(in)
        })
      }
    }

    def serverLogic(conn: Tcp.IncomingConnection)(implicit system: ActorSystem): Flow[ByteString, ByteString, NotUsed] =
      Flow.fromGraph(GraphDSL.create() { implicit b =>

        import GraphDSL.Implicits._

        val welcome = Source.single(ByteString(s"Welcome port ${conn.remoteAddress}!\n"))

        val logic = b.add(
          Flow[ByteString]
          .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 256, allowTruncation = true))
          .map(_.utf8String)
          .map { msg => system.log.info(s"Server received: $msg"); msg }
          .via(closeConnection)
          .map(ByteString(_)))

        val concat = b.add(Concat[ByteString]())
        welcome ~> concat.in(0)

        logic.outlet ~> concat.in(1)

        FlowShape(logic.in, concat.out)

      })

    def mkServer(address: String, port: Int)(implicit system: ActorSystem, materializer: Materializer): Unit = {
      import system.dispatcher

      val connectionHandler = Sink.foreach[Tcp.IncomingConnection] { conn =>
        println(s"Incoming connection from: ${conn.remoteAddress}")
        conn.handleWith(serverLogic(conn))
      }

      val incomingConnection = Tcp().bind(address, port)
      val binding = incomingConnection.to(connectionHandler).run()

      binding onComplete {
        case Success(b) =>
          println(s"Server started, listening on: ${b.localAddress}")

        case Failure(e) =>
          println(s"Server could not be bound to $address:$port: ${e.getMessage}")
      }
    }

    def mkAkkaServer() = {
      val address = "127.0.0.1"
      val port = 6666
      
      implicit val server = ActorSystem("Server")
      implicit val materializer = ActorMaterializer()
      mkServer(address, port)
    }

    mkAkkaServer()
  }
}
