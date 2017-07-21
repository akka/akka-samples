package sample.cluster.factorial

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.cluster.ClusterEvent._
import akka.cluster.Cluster
import scala.concurrent.Await
import scala.concurrent.duration._

class Factorial extends Actor with ActorLogging {
  val cluster = Cluster(context.system)

  if (cluster.selfRoles.contains("frontend")) {
    context.actorOf(Props(new FactorialFrontend(200, true)), name="frontend")
  }

  if (cluster.selfRoles.contains("backend")) {
    context.actorOf(Props[FactorialBackend], name="backend")
    context.actorOf(Props[MetricsListener], name="metricsListener")
  }

  override def preStart(): Unit = {
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents, classOf[MemberEvent], classOf[UnreachableMember])
  }

  override def postStop(): Unit = {
    cluster.unsubscribe(self)
  }

  override def receive = {
    case MemberUp(member) =>
      log.info("Member is Up: {}", member.address)
    case UnreachableMember(member) =>
      log.info("Member detected as unreachable: {}", member)
    case MemberRemoved(member, previousStatus) =>
      log.info(
        "Member is Removed: {} after {}",
        member.address, previousStatus)
    case _: MemberEvent => // ignore
  }
}

object Factorial {
  def main(args: Array[String]): Unit = {
    val systemName = sys.props.get("sample.cluster.actor-system-name").getOrElse(
      throw new IllegalArgumentException("Property sample.cluster.actor-system-name must be defined")
    )

    val system = ActorSystem(systemName)

    sys.addShutdownHook {
      Await.result(system.terminate(), 30.seconds)
    }

    system.actorOf(Props[Factorial], name = "factorial")
  }
}