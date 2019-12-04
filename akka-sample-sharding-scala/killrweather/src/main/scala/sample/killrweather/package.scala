package sample

import java.net.{DatagramSocket, InetSocketAddress}
import java.nio.channels.DatagramChannel

import scala.util.control.NonFatal

package object killrweather {

  def findHttpPort(attempt: Int): Option[Int] = {
    val ds: DatagramSocket = DatagramChannel.open().socket()
    try {
      ds.bind(new InetSocketAddress("localhost", attempt))
      Some(attempt)
    } catch {
      case NonFatal(e) =>
        ds.close()
        println(s"Unable to bind to port $attempt for http server to send data: ${e.getMessage}")
        None
    } finally
      ds.close()
  }
}
