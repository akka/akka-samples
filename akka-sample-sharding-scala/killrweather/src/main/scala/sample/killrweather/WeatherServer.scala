package sample.killrweather

import scala.util.{Failure, Success}
import scala.concurrent.duration._

import akka.actor.typed.ActorSystem
import akka.actor.CoordinatedShutdown
import akka.{Done, actor => classic}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route

private[killrweather] final class WeatherServer(routes: Route, port: Int, system: ActorSystem[_]) {
  import akka.actor.typed.scaladsl.adapter._
  implicit val classicSystem: classic.ActorSystem = system.toClassic
  private val shutdown = CoordinatedShutdown(classicSystem)

  import system.executionContext

 def start(): Unit = {
   Http().bindAndHandle(routes, "localhost", port).onComplete {
     case Success(binding) =>
       val address = binding.localAddress
       system.log.info("WeatherServer online at http://{}:{}/", address.getHostString, address.getPort)

       shutdown.addTask(CoordinatedShutdown.PhaseServiceUnbind, "http-unbind") { () =>
         binding.unbind().map(_ => Done)
       }

       shutdown.addTask(CoordinatedShutdown.PhaseServiceRequestsDone, "http-graceful-terminate") { () =>
         binding.terminate(10.seconds).map(_ => Done)
       }

       shutdown.addTask(CoordinatedShutdown.PhaseServiceStop, "http-shutdown") { () =>
         Http().shutdownAllConnectionPools().map(_ => Done)
       }
     case Failure(ex) =>
       system.log.error("Failed to bind HTTP endpoint, terminating system", ex)
       system.terminate()
   }
 }

}
