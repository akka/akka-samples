package sample.cluster.client.grpc;

import akka.NotUsed;
import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;
import akka.stream.KillSwitches;
import akka.stream.Materializer;
import akka.stream.OverflowStrategy;
import akka.stream.SharedKillSwitch;
import akka.stream.WatchedActorTerminatedException;
import akka.stream.javadsl.Source;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * This actor is intended to be used on an external node that is not member
 * of the cluster. It acts like a gateway for sending messages to actors
 * somewhere in the cluster. With service discovery and Akka gRPC it will establish
 * a connection to a {@link ClusterClientReceptionist} somewhere in the cluster.
 * <p>
 * You can send messages via the `ClusterClient` to any actor in the cluster
 * that is registered in the {@link ClusterClientReceptionist}.
 * Messages are wrapped in {@link ClusterClient.Send}, {@link ClusterClient.SendToAll}
 * or {@link ClusterClient.Publish}.
 * <p>
 * 1. {@link ClusterClient.Send} -
 * The message will be delivered to one recipient with a matching path, if any such
 * exists. If several entries match the path the message will be delivered
 * to one random destination. The sender of the message can specify that local
 * affinity is preferred, i.e. the message is sent to an actor in the same local actor
 * system as the used receptionist actor, if any such exists, otherwise random to any other
 * matching entry.
 * <p>
 * 2. {@link ClusterClient.SendToAll} -
 * The message will be delivered to all recipients with a matching path.
 * <p>
 * 3. {@link ClusterClient.Publish} -
 * The message will be delivered to all recipients Actors that have been registered as subscribers to
 * to the named topic.
 * <p>
 * Use the factory method {@link ClusterClient#props ClusterClient.props}) to create the
 * `akka.actor.Props` for the actor.
 * <p>
 * If the receptionist is not currently available, the client will buffer the messages
 * and then deliver them when the connection to the receptionist has been established.
 * The size of the buffer is configurable and it can be disabled by using a buffer size
 * of 0. When the buffer is full old messages will be dropped when new messages are sent
 * via the client.
 * <p>
 * Note that this is a best effort implementation: messages can always be lost due to the distributed
 * nature of the actors involved.
 */
public class ClusterClient extends AbstractLoggingActor {

  /**
   * Factory method for `ClusterClient` `akka.actor.Props`.
   */
  public static Props props(ClusterClientSettings settings, Materializer materializer) {
    return Props.create(ClusterClient.class, () -> new ClusterClient(settings, materializer));
  }

  public interface Command {}

  public static class Send implements Command {
    public final String path;
    public final Object msg;
    public final boolean localAffinity;

    public Send(String path, Object msg, boolean localAffinity) {
      this.path = path;
      this.msg = msg;
      this.localAffinity = localAffinity;
    }

    /**
     * Convenience constructor with `localAffinity` false
     */
    public Send(String path, Object msg) {
      this(path, msg, false);
    }
  }

  /**
   * More efficient than `Send` for single request-reply interaction
   */
  public static class SendAsk implements Command {
    public final String path;
    public final Object msg;
    public final boolean localAffinity;

    public SendAsk(String path, Object msg, boolean localAffinity) {
      this.path = path;
      this.msg = msg;
      this.localAffinity = localAffinity;
    }

    /**
     * Convenience constructor with `localAffinity` false
     */
    public SendAsk(String path, Object msg) {
      this(path, msg, false);
    }
  }

  public static class SendToAll implements Command {
    public final String path;
    public final Object msg;

    public SendToAll(String path, Object msg) {
      this.path = path;
      this.msg = msg;
    }
  }

  public static class Publish implements Command {
    public final String topic;
    public final Object msg;

    public Publish(String topic, Object msg) {
      this.topic = topic;
      this.msg = msg;
    }
  }

  private static ClusterClientReceptionistServiceClient createClientStub(ClusterClientSettings settings,
      Materializer mat) {
    return ClusterClientReceptionistServiceClient.create(settings.grpcClientSettings,mat, mat.executionContext());
  }

  private static CompletionStage<ActorRef> newSession(
      ClusterClientSettings settings,
      ClusterClientReceptionistServiceClient receptionistServiceClient,
      ActorRef sender,
      SharedKillSwitch killSwitch,
      LoggingAdapter log,
      ClusterClientSerialization serialization,
      Materializer mat) {

    CompletableFuture<ActorRef> sessionReqRefPromise = new CompletableFuture<>();

    log.info("New session for {}", sender);
    receptionistServiceClient
      .newSession(
        Source
          .actorRef(
            // never complete from stream element
            elem -> Optional.empty(),
            // never fail from stream element
            elem -> Optional.empty(),
            settings.bufferSize,
            OverflowStrategy.dropNew()
            )
          .via(killSwitch.flow())
          .map(msg -> {
            if (msg instanceof Send) {
              Send send = (Send) msg;
              Payload payload = serialization.serializePayload(send.msg);
              return Req.newBuilder()
                .setSend(SendReq.newBuilder()
                  .setPath(send.path)
                  .setLocalAffinity(send.localAffinity)
                  .setPayload(payload))
                  .build();
            } else if (msg instanceof SendToAll) {
              SendToAll sendToAll = (SendToAll) msg;
              Payload payload = serialization.serializePayload(sendToAll.msg);
              return Req.newBuilder()
                .setSendToAll(SendToAllReq.newBuilder()
                  .setPath(sendToAll.path)
                  .setPayload(payload))
                  .build();
            } else if (msg instanceof Publish) {
              Publish publish = (Publish) msg;
              Payload payload = serialization.serializePayload(publish.msg);
              return Req.newBuilder()
                .setPublish(PublishReq.newBuilder()
                  .setTopic(publish.topic)
                  .setPayload(payload))
                  .build();
            } else {
              throw new IllegalArgumentException("Unknown message type: " + msg.getClass());
            }
            }
          )
          .mapMaterializedValue(sessionReqRef -> {
              sessionReqRefPromise.complete(sessionReqRef);
              return NotUsed.getInstance();
            }
          ))
      .watch(sender) // end session when original sender terminates
      .recoverWithRetries(-1, WatchedActorTerminatedException.class, Source::empty)
      .map(rsp ->
        serialization.deserializePayload(rsp.getPayload())
      )
      .runForeach(msg -> sender.tell(msg, ActorRef.noSender()), mat)
      .whenComplete((result, exc) -> {
        if (exc == null)
          log.info("Session completed successfully for {}: {}", sender, result);
        else
          log.info("Session completed with failure for {}: {}", sender, exc);
      });

    return sessionReqRefPromise;
  }

  private static CompletionStage<Object> askSend(
    ClusterClientReceptionistServiceClient receptionistServiceClient,
    SendAsk send,
    ClusterClientSerialization serialization) {
    Payload payload = serialization.serializePayload(send.msg);
    SendReq sendReq = SendReq.newBuilder()
      .setPath(send.path)
      .setLocalAffinity(send.localAffinity)
      .setPayload(payload)
      .build();
    return receptionistServiceClient.askSend(sendReq).thenApply( rsp ->
      serialization.deserializePayload(rsp.getPayload())
    );
  }


  private final ClusterClientSettings settings;
  private final Materializer materializer;
  private final ClusterClientReceptionistServiceClient receptionistServiceClient;
  private final ClusterClientSerialization serialization = new ClusterClientSerialization(getContext().getSystem());
  // Original sender -> stream Source.actorRef of the session
  private final Map<ActorRef, CompletionStage<ActorRef>> sessionRef = new HashMap<>();
  private final SharedKillSwitch killSwitch = KillSwitches.shared(getSelf().path().name());

  private ClusterClient(ClusterClientSettings settings, Materializer materializer) {
    this.settings = settings;
    this.materializer = materializer;
    this.receptionistServiceClient = createClientStub(settings, materializer);
  }


  @Override
  public void postStop() throws Exception {
    killSwitch.shutdown();
    super.postStop();
  }

  @Override
  public Receive createReceive() {
    return ReceiveBuilder.create()
      .match(SendAsk.class, this::onSendAsk)
      .match(Command.class, this::onCommand)
      .match(Terminated.class, this::onTerminated)
      .build();
  }

  private void onSendAsk(SendAsk send) {
    Patterns.pipe(
      ClusterClient.askSend(receptionistServiceClient, send, serialization), getContext().getDispatcher())
        .to(getSender());
  }

  private void onCommand(Command cmd) {
    final ActorRef originalSender = getSender();
    CompletionStage<ActorRef> session = sessionRef.get(originalSender);
    if (session == null) {
      session = newSession(settings, receptionistServiceClient, originalSender, killSwitch, log(), serialization, materializer);
      sessionRef.put(originalSender, session);
    }

    getContext().watch(originalSender);
    session.thenAccept(ref -> ref.tell(cmd, ActorRef.noSender()));
  }

  private void onTerminated(Terminated terminated) {
    sessionRef.remove(terminated.getActor());
  }

}

