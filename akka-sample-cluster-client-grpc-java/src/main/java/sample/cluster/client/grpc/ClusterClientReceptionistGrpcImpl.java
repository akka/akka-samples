package sample.cluster.client.grpc;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.event.LoggingAdapter;
import akka.pattern.Patterns;
import akka.stream.Materializer;
import akka.stream.OverflowStrategy;
import akka.stream.javadsl.Source;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

class ClusterClientReceptionistGrpcImpl implements ClusterClientReceptionistService {
  private final ClusterReceptionistSettings settings;
  private final ActorRef pubSubMediator;
  private final ClusterClientSerialization serialization;
  private final Materializer materializer;
  private final LoggingAdapter log;

  ClusterClientReceptionistGrpcImpl(ClusterReceptionistSettings settings, ActorRef pubSubMediator, ClusterClientSerialization serialization, Materializer materializer, LoggingAdapter log) {
    this.settings = settings;
    this.pubSubMediator = pubSubMediator;
    this.serialization = serialization;
    this.materializer = materializer;
    this.log = log;
  }

  @Override
  public Source<Rsp, NotUsed> newSession(Source<Req, NotUsed> in) {
    final String sessionId = UUID.randomUUID().toString();
    log.info("New session [{}]", sessionId);
    return Source
      .actorRef(
        // never complete from stream element
        elem -> Optional.empty(),
        // never fail from stream element
        elem -> Optional.empty(),
        settings.bufferSize,
        OverflowStrategy.dropNew())
      .map( rsp -> {
        Payload payload = serialization.serializePayload(rsp);
        return Rsp.newBuilder().setPayload(payload).build();
      })
      .mapMaterializedValue( sessionRspRef -> {
        in.runForeach( req -> {
          if (req.getReqCase() == Req.ReqCase.SEND) {
            SendReq sendReq = req.getSend();
            Object msg = serialization.deserializePayload(sendReq.getPayload());
            // using sessionRspRef as sender so that replies are emitted to the response stream back to the client
            pubSubMediator.tell(
              new DistributedPubSubMediator.Send(sendReq.getPath(), msg, sendReq.getLocalAffinity()),
              sessionRspRef);
          } else if (req.getReqCase() == Req.ReqCase.SENDTOALL) {
            SendToAllReq sendToAllReq = req.getSendToAll();
            Object msg = serialization.deserializePayload(sendToAllReq.getPayload());
            pubSubMediator.tell(
              new DistributedPubSubMediator.SendToAll(sendToAllReq.getPath(), msg),
              sessionRspRef);
          } else if (req.getReqCase() == Req.ReqCase.PUBLISH) {
            PublishReq publishReq = req.getPublish();
            Object msg = serialization.deserializePayload(publishReq.getPayload());
            pubSubMediator.tell(
              new DistributedPubSubMediator.Publish(publishReq.getTopic(), msg),
              sessionRspRef);
          } else {
            throw new IllegalArgumentException("Unknown request type");
          }

        }, materializer)
          .whenComplete((result, exc) -> {
            if (exc == null)
              log.info("Session [{}] completed successfully: {}", sessionId, result);
            else
              log.info("Session [{}] completed with failure: {}", sessionId, exc);
          });

        return NotUsed.getInstance();
      });
  }

  @Override
  public CompletionStage<Rsp> askSend(SendReq sendReq) {
    try {
      Object msg = serialization.deserializePayload(sendReq.getPayload());
      return Patterns.ask(
        pubSubMediator,
        new DistributedPubSubMediator.Send(sendReq.getPath(), msg, sendReq.getLocalAffinity()),
        settings.askSendTimeout)
        .thenApply( rsp -> {
        Payload payload = serialization.serializePayload(rsp);
        return Rsp.newBuilder().setPayload(payload).build();
      });
    } catch (RuntimeException e) {
      // deserialization error
      CompletableFuture<Rsp> result = new CompletableFuture<>();
      result.completeExceptionally(e);
      return result;
    }
  }

}
