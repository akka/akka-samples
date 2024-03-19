/*
 * Copyright (C) 2009-2024 Lightbend Inc. <https://www.lightbend.com>
 */
package sample.persistence.res.counter

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.ReplicatedSharding
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.persistence.typed.ReplicaId
import akka.stream.scaladsl.Source
import akka.util.{ByteString, Timeout}
import sample.persistence.res.counter.ThumbsUpCounter.State

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object ThumbsUpHttp {

  def route(selfReplica: ReplicaId, res: ReplicatedSharding[ThumbsUpCounter.Command])(implicit system: ActorSystem[_]): Route = {

    import akka.http.scaladsl.server.Directives._

    implicit val timeout: Timeout = Timeout(10.seconds)
    implicit val ec: ExecutionContext = system.executionContext

    pathPrefix("thumbs-up") {
      concat(
        // example: curl http://127.0.0.1:22551/thumbs-up/a
        get {
          path(Segment) { resourceId =>
            onComplete(res.entityRefsFor(resourceId)(selfReplica).ask[State](replyTo => ThumbsUpCounter.GetUsers(resourceId, replyTo))) {
              case Success(state) =>
                val s = Source.fromIterator(() => state.users.iterator)
                  .intersperse("\n")
                  .map(ByteString(_))
                complete(HttpResponse(entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, s)))
              case Failure(ex) => complete(StatusCodes.InternalServerError, ex.toString)
            }
          }
        },
        // example: curl -X POST http://127.0.0.1:22551/thumbs-up/a/u1
        post {
          path(Segment / Segment) { (resourceId, userId) =>
            onComplete(res.entityRefsFor(resourceId)(selfReplica).ask[Long](replyTo => ThumbsUpCounter.GiveThumbsUp(resourceId, userId, replyTo))) {
              case Success(i) => complete(i.toString)
              case Failure(ex) => complete(StatusCodes.BadRequest, ex.toString)
            }
          }
        }
      )
    }


  }
}
