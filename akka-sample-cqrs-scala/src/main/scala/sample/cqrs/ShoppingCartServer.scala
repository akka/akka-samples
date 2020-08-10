package sample.cqrs

import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success

import akka.actor.CoordinatedShutdown
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.Done

class ShoppingCartServer(routes: Route, port: Int)(implicit system: ActorSystem[_]) {
  private val shutdown = CoordinatedShutdown(system)

  import system.executionContext

  def start(): Unit = {
    Http().newServerAt("localhost", port).bind(routes)
      .map(_.addToCoordinatedShutdown(3.seconds))
      .onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info("Shopping online at http://{}:{}/", address.getHostString, address.getPort)

        shutdown.addTask(CoordinatedShutdown.PhaseServiceRequestsDone, "http-graceful-terminate") { () =>
          binding.terminate(10.seconds).map { _ =>
            system.log
              .info("Shopping http://{}:{}/ graceful shutdown completed", address.getHostString, address.getPort)
            Done
          }
        }
      case Failure(ex) =>
        system.log.error("Failed to bind HTTP endpoint, terminating system", ex)
        system.terminate()
    }
  }

}
