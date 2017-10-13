package akka_tut.stream

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{Flow, Framing, Tcp}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.util.ByteString

import scala.io.StdIn

/**
  * 2017-10-13
  */
object ComplexTcpClient {

  def main(args: Array[String]): Unit = {
    implicit val client = ActorSystem("SimpleTcpClient")
    implicit val meterializer = ActorMaterializer()

    val address = "127.0.0.1"
    val port = 6666

    val connection = Tcp().outgoingConnection(address, port)

    val closeClient = new GraphStage[FlowShape[String, String]] {
      val in = Inlet[String]("closeClient.in")
      val out = Outlet[String]("closeClient.out")

      override def shape = FlowShape(in, out)

      override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
        setHandler(in, new InHandler {
          override def onPush(): Unit = grab(in) match {
            case "BYE" =>
              println("Connection closed")
              completeStage()

            case msg =>
              println(msg)
              push(out, msg)
          }
        })

        setHandler(out, new OutHandler {
          override def onPull(): Unit = pull(in)
        })
      }
    }

    val flow = Flow[ByteString]
      .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 256, allowTruncation = true))
      .map(_.utf8String)
      .via(closeClient)
      .map(_ => StdIn.readLine("> "))
      .map(_ + "\n")
      .map(ByteString(_))

    connection.join(flow).run()
  }
}
