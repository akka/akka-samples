/**
 * Copyright (C) 2019 Lightbend Inc. <https://www.lightbend.com>
 */
package akka.cluster.client.grpc

import java.util.UUID

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal

import akka.NotUsed
import akka.actor.ActorRef
import akka.cluster.pubsub.DistributedPubSubMediator
import akka.event.LoggingAdapter
import akka.stream.Materializer
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Source
import akka.util.Timeout

class ClusterClientReceptionistGrpcImpl(pubSubMediator: ActorRef, serialization: ClusterClientSerialization)(
    implicit
    mat: Materializer,
    log: LoggingAdapter)
    extends ClusterClientReceptionistService {

  override def newSession(in: Source[Req, NotUsed]): Source[Rsp, NotUsed] = {
    val sessionId = UUID.randomUUID().toString
    log.info("New session [{}]", sessionId)
    Source
      .actorRef[Any](bufferSize = 1000, OverflowStrategy.dropNew) // FIXME config
      .map { rsp =>
        val payload = serialization.serializePayload(rsp)
        Rsp(Some(payload))
      }
      .mapMaterializedValue { sessionRspRef =>
        in.runForeach { req =>
            if (req.req.isSend) {
              val sendReq = req.getSend
              val msg = serialization.deserializePayload(sendReq.payload.get)
              // using sessionRspRef as sender so that replies are emitted to the response stream back to the client
              pubSubMediator.tell(
                DistributedPubSubMediator.Send(sendReq.path, msg, sendReq.localAffinity),
                sessionRspRef)
            } else if (req.req.isSendToAll) {
              val sendToAllReq = req.getSendToAll
              val msg = serialization.deserializePayload(sendToAllReq.payload.get)
              pubSubMediator.tell(DistributedPubSubMediator.SendToAll(sendToAllReq.path, msg), sessionRspRef)
            } else if (req.req.isPublish) {
              val publishReq = req.getPublish
              val msg = serialization.deserializePayload(publishReq.payload.get)
              pubSubMediator.tell(DistributedPubSubMediator.Publish(publishReq.topic, msg), sessionRspRef)
            } else {
              throw new IllegalArgumentException("Unknown request type")
            }

          }
          .onComplete { result =>
            log.info("Session [{}] completed: {}", sessionId, result)
          }(mat.executionContext)
        NotUsed
      }
  }

  override def askSend(sendReq: SendReq): Future[Rsp] = {
    try {
      import akka.pattern.ask
      implicit val timeout = Timeout(5.seconds) // FIXME config
      implicit val ec = mat.executionContext
      val msg = serialization.deserializePayload(sendReq.payload.get)
      (pubSubMediator ? DistributedPubSubMediator.Send(sendReq.path, msg, sendReq.localAffinity)).map { rsp =>
        val payload = serialization.serializePayload(rsp)
        Rsp(Some(payload))
      }
    } catch {
      case NonFatal(e) =>
        // ask timeout or deserialization error
        Future.failed(e)

    }
  }
}
