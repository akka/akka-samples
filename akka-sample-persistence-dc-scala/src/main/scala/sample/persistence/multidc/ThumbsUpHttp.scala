package sample.persistence.multidc

import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import akka.util.Timeout

object ThumbsUpHttp {

  def startServer(httpHost: String, httpPort: Int, counterRegion: ActorRef)(implicit system: ActorSystem) = {

    import akka.http.scaladsl.server.Directives._

    implicit val mat = ActorMaterializer()
    import system.dispatcher
    implicit val timeout: Timeout = Timeout(10.seconds)

    val api = pathPrefix("thumbs-up") {
      concat(
        // example: curl http://127.0.0.1:22551/thumbs-up/a
        get {
          path(Segment) { resourceId ⇒
            onComplete((counterRegion ? ThumbsUpCounter.GetUsers(resourceId))
              .mapTo[ThumbsUpCounter.State]) { state =>
              val s = Source.fromIterator(() ⇒ state.get.users.iterator)
                .intersperse("\n")
                .map(ByteString(_))

              complete(HttpResponse(entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, s)))
            }
          }
        },
        // example: curl -X POST http://127.0.0.1:22551/thumbs-up/a/u1
        post {
          path(Segment / Segment) { (resourceId, userId) ⇒
            onComplete((counterRegion ? ThumbsUpCounter.GiveThumbsUp(resourceId, userId)).mapTo[Int]) {
              case Success(i) => complete(i.toString)
              case Failure(ex) => complete(StatusCodes.BadRequest, ex.toString)
            }
          }
        }
      )
    }

    Http().bindAndHandle(api, httpHost, httpPort).onComplete {
      case Success(_) => system.log.info("HTTP Server bound to http://{}:{}", httpHost, httpPort)
      case Failure(ex) => system.log.error(ex, "Failed to bind HTTP Server to http://{}:{}", httpHost, httpPort)
    }

  }
}
