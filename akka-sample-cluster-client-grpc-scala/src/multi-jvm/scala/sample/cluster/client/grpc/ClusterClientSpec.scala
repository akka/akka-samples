package sample.cluster.client.grpc

import scala.concurrent.Await
import scala.concurrent.duration._

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.cluster.Cluster
import akka.cluster.pubsub._
import akka.remote.testconductor.RoleName
import akka.remote.testkit.MultiNodeConfig
import akka.remote.testkit.MultiNodeSpec
import akka.stream.Materializer
import akka.testkit._
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Matchers
import org.scalatest.WordSpecLike

object ClusterClientSpec extends MultiNodeConfig {
  val client = role("client")
  val first = role("first")
  val second = role("second")
  val third = role("third")
  val fourth = role("fourth")

  commonConfig(ConfigFactory.parseString("""
    akka.loglevel = INFO
    akka.http.server.preview.enable-http2 = on
    akka.actor.provider = "cluster"
    akka.remote.artery.canonical.port = 0
    """).withFallback(ConfigFactory.load()))

  val grpcPorts: Map[RoleName, Int] =
    Map(first -> 50051, second -> 50052, third -> 50053, fourth -> 50054)

  // test is binding to localhost hostname, so will not work as multi-node

  nodeConfig(first)(ConfigFactory.parseString(s"""
    sample.cluster.client.grpc.receptionist.canonical.port = ${grpcPorts(first)}
    """))

  nodeConfig(second)(ConfigFactory.parseString(s"""
    sample.cluster.client.grpc.receptionist.canonical.port = ${grpcPorts(second)}
    """))

  nodeConfig(third)(ConfigFactory.parseString(s"""
    sample.cluster.client.grpc.receptionist.canonical.port = ${grpcPorts(third)}
    """))

  nodeConfig(fourth)(ConfigFactory.parseString(s"""
    sample.cluster.client.grpc.receptionist.canonical.port = ${grpcPorts(fourth)}
    """))

  testTransport(on = true)

  case class Reply(msg: String, node: HostPort) extends CborSerializable

  class TestService(testActor: ActorRef) extends Actor {
    def receive = {
      case "shutdown" =>
        context.system.terminate()
      case msg: String =>
        testActor.forward(msg)
        sender() ! Reply(msg + "-ack", ClusterClientReceptionist(context.system).settings.hostPort)
    }
  }

  class Service extends Actor {
    def receive = {
      case msg => sender() ! msg
    }
  }

}

class ClusterClientMultiJvmNode1 extends ClusterClientSpec
class ClusterClientMultiJvmNode2 extends ClusterClientSpec
class ClusterClientMultiJvmNode3 extends ClusterClientSpec
class ClusterClientMultiJvmNode4 extends ClusterClientSpec
class ClusterClientMultiJvmNode5 extends ClusterClientSpec

class ClusterClientSpec
    extends MultiNodeSpec(ClusterClientSpec)
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with ImplicitSender {
  import ClusterClientSpec._

  private implicit val materializer: Materializer = Materializer(system)

  override def initialParticipants = roles.size

  override def beforeAll() = multiNodeSpecBeforeAll()

  override def afterAll() = multiNodeSpecAfterAll()

  def join(from: RoleName, to: RoleName): Unit = {
    runOn(from) {
      Cluster(system).join(node(to).address)
      createReceptionist()
    }
    enterBarrier(from.name + "-joined")
  }

  def createReceptionist(): Unit = ClusterClientReceptionist(system)

  def awaitCount(expected: Int): Unit = {
    awaitAssert {
      DistributedPubSub(system).mediator ! DistributedPubSubMediator.Count
      expectMsgType[Int] should ===(expected)
    }
  }

  def roleName(addr: HostPort): Option[RoleName] = {
    grpcPorts
      .collect {
        case (role, port) => (port, role)
      }
      .toMap
      .get(addr.port)
  }

  "A ClusterClient" must {

    "startup cluster" in within(30.seconds) {
      join(first, first)
      join(second, first)
      join(third, first)
      join(fourth, first)
      runOn(fourth) {
        val service =
          system.actorOf(Props(classOf[TestService], testActor), "testService")
        ClusterClientReceptionist(system).registerService(service)
      }
      runOn(first, second, third, fourth) {
        awaitCount(1)
      }

      enterBarrier("after-1")
    }

    "communicate to actor on any node in cluster" in within(10.seconds) {
      runOn(client) {
        val c = system.actorOf(ClusterClient.props(ClusterClientSettings(system)), "client1")
        c ! ClusterClient.Send("/user/testService", "hello-1", localAffinity = true)
        expectMsgType[Reply].msg should be("hello-1-ack")
        c ! ClusterClient.Send("/user/testService", "hello-2", localAffinity = true)
        expectMsgType[Reply].msg should be("hello-2-ack")

        c ! ClusterClient.SendToAll("/user/testService", "hello-all")
        // testService is only running on fourth so only one reply
        expectMsgType[Reply].msg should be("hello-all-ack")

        system.stop(c)
      }
      runOn(fourth) {
        expectMsg("hello-1")
        expectMsg("hello-2")
        expectMsg("hello-all")
      }

      enterBarrier("after-2")
    }

    "work with ask and session" in within(10.seconds) {
      // ask with Send is inefficient but should work
      runOn(client) {
        import akka.pattern.ask
        val c = system.actorOf(ClusterClient.props(ClusterClientSettings(system)), "ask-session-client")
        implicit val timeout = Timeout(remaining)
        val reply1 = c ? ClusterClient.Send("/user/testService", "hello-1-request", localAffinity = true)
        Await.result(reply1.mapTo[Reply], remaining).msg should be("hello-1-request-ack")
        val reply2 = c ? ClusterClient.Send("/user/testService", "hello-2-request", localAffinity = true)
        Await.result(reply2.mapTo[Reply], remaining).msg should be("hello-2-request-ack")
        system.stop(c)
      }
      runOn(fourth) {
        expectMsg("hello-1-request")
        expectMsg("hello-2-request")
      }

      enterBarrier("after-3")
    }

    "work with explicit ask" in within(10.seconds) {
      runOn(client) {
        import akka.pattern.ask
        val c = system.actorOf(ClusterClient.props(ClusterClientSettings(system)), "ask-client")
        implicit val timeout = Timeout(remaining)
        val reply1 = c ? ClusterClient.SendAsk("/user/testService", "hello-1-request", localAffinity = true)
        Await.result(reply1.mapTo[Reply], remaining).msg should be("hello-1-request-ack")
        val reply2 = c ? ClusterClient.SendAsk("/user/testService", "hello-2-request", localAffinity = true)
        Await.result(reply2.mapTo[Reply], remaining).msg should be("hello-2-request-ack")
        system.stop(c)
      }
      runOn(fourth) {
        expectMsg("hello-1-request")
        expectMsg("hello-2-request")
      }

      enterBarrier("after-4")
    }

    "demonstrate usage" in within(15.seconds) {

      def host1 = first
      def host2 = second
      def host3 = third

      //#server
      runOn(host1) {
        val serviceA = system.actorOf(Props[Service], "serviceA")
        ClusterClientReceptionist(system).registerService(serviceA)
      }

      runOn(host2, host3) {
        val serviceB = system.actorOf(Props[Service], "serviceB")
        ClusterClientReceptionist(system).registerService(serviceB)
      }
      //#server

      runOn(host1, host2, host3, fourth) {
        awaitCount(4)
      }
      enterBarrier("services-replicated")

      //#client
      runOn(client) {
        val c = system.actorOf(ClusterClient.props(ClusterClientSettings(system)), "client")
        c ! ClusterClient.Send("/user/serviceA", "hello", localAffinity = true)
        c ! ClusterClient.SendToAll("/user/serviceB", "hi")
      }
      //#client

      runOn(client) {
        // note that "hi" was sent to 2 "serviceB"
        receiveN(3).toSet should ===(Set("hello", "hi"))
      }

      enterBarrier("after-5")
    }

  }
}
