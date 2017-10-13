package akka_tut.stream

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.{Flow, Framing, Sink, Tcp}
import akka.util.ByteString

import scala.util.{Failure, Success}

/**
  * 2017-10-12
  */
object SimleTcpServer {

  def main(args: Array[String]): Unit = {
    val address = "127.0.01"
    val port = 6666

    val serverLogic = Flow[ByteString]
      .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 256, allowTruncation = true))
      .map(_.utf8String)
      .map(msg => s"Server hereby responds to message: $msg\n")
      .map(ByteString(_))

    val serverLogic2/*: Flow[ByteString, ByteString, Unit]*/ = {
      val delimiter = Framing.delimiter(
        ByteString("\n"),
        maximumFrameLength = 256,
        allowTruncation = true
      )

      val receiver = Flow[ByteString].map { bytes =>
        val message = bytes.utf8String
        println(s"Server received: $message")
        message
      }

      val responder = Flow[String].map { message =>
        val answer = s"Server hereby responds to message: $message\n"
        ByteString(answer)
      }

      Flow[ByteString]
        .via(delimiter)
        .via(receiver)
        .via(responder)
    }

    def mkServer(address: String, port: Int)(implicit system: ActorSystem, materializer: Materializer): Unit = {
      import system.dispatcher

      val connectionHandler = Sink.foreach[Tcp.IncomingConnection] { conn =>
        println(s"Incoming connection from: ${conn.remoteAddress}")
        conn.handleWith(serverLogic2)
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
      implicit val server = ActorSystem("Server")
      implicit val materializer = ActorMaterializer()
      mkServer(address, port)
    }

    mkAkkaServer()
  }
}
