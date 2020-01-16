package sample.cluster.client.grpc;


import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.Cluster;
import akka.cluster.pubsub.DistributedPubSub;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;
import akka.stream.Materializer;
import akka.testkit.javadsl.TestKit;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

// order of test methods matter in this test
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ClusterClientTest {

  private static ActorSystem clientNode;
  private static ActorSystem clusterNode1;
  private static ActorSystem clusterNode2;
  private static ActorSystem clusterNode3;
  private static ActorSystem clusterNode4;

  private static TestKit probe0;
  private static TestKit probe1;
  private static TestKit probe2;
  private static TestKit probe3;
  private static TestKit probe4;

  private static Config clusterConfig(int grpcPort) {
   return  ConfigFactory.parseString(
        "akka.actor.provider = cluster \n" +
          "akka.remote.artery.canonical.port = 0 \n" +
          "sample.cluster.client.grpc.receptionist.canonical.port = " + grpcPort + " \n" +
          "").withFallback(ConfigFactory.load());
  }

  @BeforeClass
  public static void setup() {
    Config clientConfig =
      ConfigFactory.parseString(
        "akka.actor.provider = local \n" +
          "").withFallback(ConfigFactory.load());

    clientNode = ActorSystem.create("ClusterClientTest");
    clusterNode1 = ActorSystem.create("ClusterClientTest", clusterConfig(50051));
    clusterNode2 = ActorSystem.create("ClusterClientTest", clusterConfig(50052));
    clusterNode3 = ActorSystem.create("ClusterClientTest", clusterConfig(50053));
    clusterNode4 = ActorSystem.create("ClusterClientTest", clusterConfig(50054));

    probe0 = new TestKit(clientNode);
    probe1 = new TestKit(clusterNode1);
    probe2 = new TestKit(clusterNode2);
    probe3 = new TestKit(clusterNode3);
    probe4 = new TestKit(clusterNode4);
  }

  @AfterClass
  public static void teardown() {
    TestKit.shutdownActorSystem(clientNode);
    clientNode = null;
    TestKit.shutdownActorSystem(clusterNode1);
    clusterNode1 = null;
    TestKit.shutdownActorSystem(clusterNode2);
    clusterNode2 = null;
    TestKit.shutdownActorSystem(clusterNode3);
    clusterNode3 = null;
    TestKit.shutdownActorSystem(clusterNode4);
    clusterNode4 = null;
  }

  static class Reply implements CborSerializable {
    public final String msg;
    public final ClusterReceptionistSettings.HostPort node;

    Reply(String msg, ClusterReceptionistSettings.HostPort node) {
      this.msg = msg;
      this.node = node;
    }
  }

  static class TestService extends AbstractActor {

    TestService(ActorRef testActor) {
      this.testActor = testActor;
    }

    public static Props props(ActorRef testActor) {
      return Props.create(TestService.class, () -> new TestService(testActor));
    }

    private final ActorRef testActor;

    @Override
    public Receive createReceive() {
      return ReceiveBuilder.create()
        .matchEquals("shutdown", notUsed -> getContext().getSystem().terminate())
        .matchAny(msg -> {
          testActor.tell(msg, getSelf());
          getSender().tell(
            new Reply(msg + "-ack",
              ClusterClientReceptionist.get(getContext().getSystem()).settings.hostPort),
            getSelf()
          );
        })
        .build();
    }
  }

  static class Service extends AbstractActor {

    public static Props props() {
      return Props.create(Service.class, () -> new Service());
    }

    @Override
    public Receive createReceive() {
      return ReceiveBuilder.create()
        .matchAny(msg -> getSender().tell(msg, getSelf()))
        .build();
    }
  }

  private void awaitCount(ActorSystem system, int expected, Duration timeout) {
    TestKit testKit = new TestKit(system);
    testKit.awaitAssert(timeout, () -> {
      DistributedPubSub.get(system).mediator().tell(DistributedPubSubMediator.getCountInstance(), testKit.getRef());
      testKit.expectMsg(expected);
      return null;
    });
  }

  @Test
  public void step1_shouldJoinNodesToCluster() {
    Cluster.get(clusterNode1).join(Cluster.get(clusterNode1).selfAddress());
    Cluster.get(clusterNode2).join(Cluster.get(clusterNode1).selfAddress());
    Cluster.get(clusterNode3).join(Cluster.get(clusterNode1).selfAddress());
    Cluster.get(clusterNode4).join(Cluster.get(clusterNode1).selfAddress());

    // initialize the receptionist
    ClusterClientReceptionist.get(clusterNode1);
    ClusterClientReceptionist.get(clusterNode2);
    ClusterClientReceptionist.get(clusterNode3);
    ClusterClientReceptionist.get(clusterNode4);

    ActorRef service4 =
      clusterNode4.actorOf(TestService.props(probe4.getTestActor()), "testService");
    ClusterClientReceptionist.get(clusterNode4).registerService(service4);

    Duration timeout = Duration.ofSeconds(15);
    awaitCount(clusterNode1, 1, timeout);
    awaitCount(clusterNode2, 1, timeout);
    awaitCount(clusterNode3, 1, timeout);
    awaitCount(clusterNode4, 1, timeout);
  }

  @Test
  public void step2_shouldCommuicateToActorOnAnyNodeInCluster() {
      Materializer materializer = Materializer.createMaterializer(clientNode);
      ActorRef c = clientNode.actorOf(ClusterClient.props(ClusterClientSettings.create(clientNode), materializer),
        "client1");
      c.tell(new ClusterClient.Send("/user/testService", "hello-1", true), probe0.getRef());
      assertEquals("hello-1-ack", probe0.expectMsgClass(Reply.class).msg);
      c.tell(new ClusterClient.Send("/user/testService", "hello-2", true), probe0.getRef());
      assertEquals("hello-2-ack", probe0.expectMsgClass(Reply.class).msg);

      probe4.expectMsg("hello-1");
      probe4.expectMsg("hello-2");

      c.tell(new ClusterClient.SendToAll("/user/testService", "hello-all"), probe0.getRef());
      // testService is only running on fourth so only one reply
      assertEquals("hello-all-ack", probe0.expectMsgClass(Reply.class).msg);

      probe4.expectMsg("hello-all");

      clientNode.stop(c);
  }

  @Test
  public void step2_shouldWorkWithAskAndSession() throws Exception {
    Materializer materializer = Materializer.createMaterializer(clientNode);
    ActorRef c = clientNode.actorOf(ClusterClient.props(ClusterClientSettings.create(clientNode), materializer),
      "ask-sesion-client");
    Duration timeout = Duration.ofSeconds(3);

    CompletionStage<Object> futureReply1 = Patterns.ask(c,
      new ClusterClient.Send("/user/testService", "hello-1-request", true), timeout);
    Reply reply1 = (Reply) futureReply1.toCompletableFuture().get(timeout.getSeconds(), TimeUnit.SECONDS);
    assertEquals("hello-1-request-ack", reply1.msg);

    CompletionStage<Object> futureReply2 = Patterns.ask(c,
      new ClusterClient.Send("/user/testService", "hello-2-request", true), timeout);
    Reply reply2 = (Reply) futureReply2.toCompletableFuture().get(timeout.getSeconds(), TimeUnit.SECONDS);
    assertEquals("hello-2-request-ack", reply2.msg);

    probe4.expectMsg("hello-1-request");
    probe4.expectMsg("hello-2-request");

    clientNode.stop(c);
  }

  @Test
  public void step2_shouldWorkWithExplicitAsk() throws Exception {
    Materializer materializer = Materializer.createMaterializer(clientNode);
    ActorRef c = clientNode.actorOf(ClusterClient.props(ClusterClientSettings.create(clientNode), materializer),
      "ask-client");
    Duration timeout = Duration.ofSeconds(3);

    CompletionStage<Object> futureReply1 = Patterns.ask(c,
      new ClusterClient.SendAsk("/user/testService", "hello-1-request", true), timeout);
    Reply reply1 = (Reply) futureReply1.toCompletableFuture().get(timeout.getSeconds(), TimeUnit.SECONDS);
    assertEquals("hello-1-request-ack", reply1.msg);

    CompletionStage<Object> futureReply2 = Patterns.ask(c,
      new ClusterClient.SendAsk("/user/testService", "hello-2-request", true), timeout);
    Reply reply2 = (Reply) futureReply2.toCompletableFuture().get(timeout.getSeconds(), TimeUnit.SECONDS);
    assertEquals("hello-2-request-ack", reply2.msg);

    probe4.expectMsg("hello-1-request");
    probe4.expectMsg("hello-2-request");

    clientNode.stop(c);
  }

  @Test
  public void step3_shouldDemonstrateUsage() {

    // Server
    ActorRef serviceA1 = clusterNode1.actorOf(Service.props(), "serviceA");
    ClusterClientReceptionist.get(clusterNode1).registerService(serviceA1);

    ActorRef serviceB2 = clusterNode2.actorOf(Service.props(), "serviceB");
    ClusterClientReceptionist.get(clusterNode2).registerService(serviceB2);

    ActorRef serviceB3 = clusterNode3.actorOf(Service.props(), "serviceB");
    ClusterClientReceptionist.get(clusterNode3).registerService(serviceB3);

    Duration timeout = Duration.ofSeconds(5);
    awaitCount(clusterNode1, 4, timeout);
    awaitCount(clusterNode2, 4, timeout);
    awaitCount(clusterNode3, 4, timeout);
    awaitCount(clusterNode4, 4, timeout);


    // Client
    Materializer materializer = Materializer.createMaterializer(clientNode);
    ActorRef c = clientNode.actorOf(ClusterClient.props(ClusterClientSettings.create(clientNode), materializer),
      "client");
    c.tell(new ClusterClient.Send("/user/serviceA", "hello", true), probe0.getRef());
    c.tell(new ClusterClient.SendToAll("/user/serviceB", "hi"), probe0.getRef());

    List<Object> replies = probe0.receiveN(3);
    assertTrue(replies.contains("hi"));
    assertTrue(replies.contains("hello"));

    clientNode.stop(c);
  }



}
