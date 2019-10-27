package sample.cluster.transformation

import scala.concurrent.duration._
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.WordSpecLike
import org.scalatest.Matchers
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.Props
import akka.actor.typed.SpawnProtocol
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.adapter._
import akka.cluster.Cluster
import akka.remote.testkit.MultiNodeConfig
import akka.remote.testkit.MultiNodeSpec
import akka.testkit.ImplicitSender
import akka.testkit.TestProbe
import akka.util.Timeout

import scala.concurrent.Await
import scala.concurrent.Future

object TransformationSampleSpecConfig extends MultiNodeConfig {
  // register the named roles (nodes) of the test
  val frontend1 = role("frontend1")
  val frontend2 = role("frontend2")
  val backend1 = role("backend1")
  val backend2 = role("backend2")
  val backend3 = role("backend3")

  def nodeList = Seq(frontend1, frontend2, backend1, backend2, backend3)

  // Extract individual sigar library for every node.
  nodeList foreach { role =>
    nodeConfig(role) {
      ConfigFactory.parseString(s"""
      # Enable metrics extension in akka-cluster-metrics.
      akka.extensions=["akka.cluster.metrics.ClusterMetricsExtension"]
      # Sigar native library extract location during tests.
      akka.cluster.metrics.native-library-extract-folder=target/native/${role.name}
      """)
    }
  }

  // this configuration will be used for all nodes
  // note that no fixed host names and ports are used
  commonConfig(ConfigFactory.parseString("""
    akka.actor.provider = cluster
    # not using Artery in test due small /dev/shm in Travis
    akka.remote.artery.enabled = off
    """))

  nodeConfig(frontend1, frontend2)(
    ConfigFactory.parseString("akka.cluster.roles =[frontend]"))

  nodeConfig(backend1, backend2, backend3)(
    ConfigFactory.parseString("akka.cluster.roles =[backend]"))
}

// need one concrete test class per node
class TransformationSampleSpecMultiJvmNode1 extends TransformationSampleSpec
class TransformationSampleSpecMultiJvmNode2 extends TransformationSampleSpec
class TransformationSampleSpecMultiJvmNode3 extends TransformationSampleSpec
class TransformationSampleSpecMultiJvmNode4 extends TransformationSampleSpec
class TransformationSampleSpecMultiJvmNode5 extends TransformationSampleSpec

abstract class TransformationSampleSpec extends MultiNodeSpec(TransformationSampleSpecConfig)
  with WordSpecLike with Matchers with BeforeAndAfterAll with ImplicitSender {

  implicit def typedSystem: ActorSystem[Nothing] = system.toTyped

  val spawnActor = system.actorOf(PropsAdapter(SpawnProtocol())).toTyped[SpawnProtocol.Command]
  def spawn[T](behavior: Behavior[T], name: String): ActorRef[T] = {
    implicit val timeout: Timeout = 3.seconds
    val f: Future[ActorRef[T]] = spawnActor.ask(SpawnProtocol.Spawn(behavior, name, Props.empty, _))

    Await.result(f, 3.seconds)
  }

  import TransformationSampleSpecConfig._

  override def initialParticipants = roles.size

  override def beforeAll() = multiNodeSpecBeforeAll()

  override def afterAll() = multiNodeSpecAfterAll()

  "The transformation sample" must {
    "illustrate how to start first frontend" in within(15 seconds) {
      runOn(frontend1) {
        // this will only run on the 'first' node
        Cluster(system) join node(frontend1).address
        val transformationFrontend = spawn(Frontend(), name = "Frontend")
        transformationFrontend ! Frontend("hello")
        expectMsgPF() {
          // no backends yet, service unavailable
          case JobFailed(_, TransformationJob("hello")) =>
        }
      }

      // this will run on all nodes
      // use barrier to coordinate test steps
      testConductor.enter("frontend1-started")
    }

    "illustrate how a backend automatically registers" in within(15 seconds) {
      runOn(backend1) {
        Cluster(system) join node(frontend1).address
        spawn(Worker(), name = "backend")
      }
      testConductor.enter("backend1-started")

      runOn(frontend1) {
        assertServiceOk()
      }

      testConductor.enter("frontend1-backend1-ok")
    }

    "illustrate how more nodes registers" in within(20 seconds) {
      runOn(frontend2) {
        Cluster(system) join node(frontend1).address
        system.actorOf(Props[TransformationFrontend], name = "frontend")
      }
      testConductor.enter("frontend2-started")

      runOn(backend2, backend3) {
        Cluster(system) join node(backend1).address
        system.actorOf(Props[TransformationBackend], name = "backend")
      }

      testConductor.enter("all-started")

      runOn(frontend1, frontend2) {
        assertServiceOk()
      }

      testConductor.enter("all-ok")

    }

  }

  def assertServiceOk(): Unit = {
    implicit val timeout: Timeout = 3.seconds
    // eventually the service should be ok,
    // backends might not have registered initially
    awaitAssert {
      val probe = TestProbe[Receptionist.Listing]()
      val found: Future[Receptionist.Listing] = typedSystem.receptionist.ask(Receptionist.Find(Worker.WorkerServiceKey, _))
      val listing = Await.result(found, 4.seconds)
      val workers = listing.serviceInstances(Worker.WorkerServiceKey)
      workers.size shouldBe >= (1)
      workers.head ! TransformationJob("hello")
      expectMsgType[TransformationResult](1.second).text should be("HELLO")
    }
  }

}
