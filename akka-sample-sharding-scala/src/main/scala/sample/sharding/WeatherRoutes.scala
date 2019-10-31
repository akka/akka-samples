package sample.sharding

import akka.actor.typed.{ ActorRef, ActorSystem }
import akka.{ actor => classic }
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.MethodDirectives.post
import akka.http.scaladsl.server.directives.RouteDirectives.complete

class WeatherRoutes(guardian: ActorRef[Protocol.Command])(implicit system: ActorSystem[_]) {

  import Protocol._
  import akka.actor.typed.scaladsl.adapter._
  implicit val classicSystem: classic.ActorSystem = system.toClassic

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import JsonFormats._

  val weather: Route =
    pathPrefix("temperature") {
      concat(pathEnd {
        concat(post {
          // TODO
          entity(as[UpdateDevice]) { data =>
            guardian ! data
            complete(StatusCodes.OK)
          }
        })
      })
    }

}
