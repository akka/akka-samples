package akka.projection.testing

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route

import scala.concurrent.duration._
import scala.util.{Failure, Success}

class HttpServer(routes: Route, port: Int)(implicit system: ActorSystem[_]) {
  import system.executionContext

  def start(): Unit = {
    Http().newServerAt("localhost", port).bind(routes)
      .map(_.addToCoordinatedShutdown(3.seconds))
      .onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info("Online at http://{}:{}/", address.getHostString, address.getPort)

      case Failure(ex) =>
        system.log.error("Failed to bind HTTP endpoint, terminating system", ex)
        system.terminate()
    }
  }

}
