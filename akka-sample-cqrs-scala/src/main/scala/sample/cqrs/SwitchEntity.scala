package sample.cqrs

import akka.actor.{ActorLogging, Props}
import akka.persistence.PersistentActor
import akka.persistence.journal.Tagged

import scala.math.abs

object SwitchEntity {

  sealed trait Command

  final case class CreateSwitch(numberOfPorts: Int) extends Command
  final case class SetPortStatus(port: Int, portEnabled: Boolean) extends Command
  final case object SendPortStatus extends Command

  sealed trait Event

  final case class SwitchCreated(n: Int) extends Event
  final case class PortStatusSet(port: Int, portEnabled: Boolean) extends Event
  final case class PortStatusSent(portStatus: Map[Int, Boolean]) extends Event

  def props: Props =
    Props(new SwitchEntity)
}

class SwitchEntity extends PersistentActor with ActorLogging {

  import SwitchEntity._

  private val settings = Settings(context.system)

  override def persistenceId: String = "SwitchEntity|" + context.self.path.name

  private def eventTag =
    s"${settings.eventProcessorSettings.tagPrefix}${abs(persistenceId.hashCode % settings.eventProcessorSettings.parallelism)}"

  private var portStatus: Option[Map[Int, Boolean]] = None

  override def receiveRecover: Receive = {
    case event: Event => applyEvent(event)
  }

  override def receiveCommand: Receive = {

    case CreateSwitch(nPorts) if portStatus.isEmpty =>
      persist(SwitchCreated(nPorts))(applyEvent)

    case CreateSwitch(nPorts) =>
      log.error("Cannot create a switch that already exists")


    case SetPortStatus(port, status) if portStatus.isDefined && port >= 0 && port < portStatus.get.size =>
      persist(PortStatusSet(port, status))(applyEvent)

    case SetPortStatus(port, status) if portStatus.isEmpty =>
      log.error("Cannot set port status on non-existing switch")

    case SetPortStatus(port, status) =>
      log.error("port number out of range")

    case SendPortStatus if portStatus.isDefined =>
      val event = PortStatusSent(portStatus.get)
      // The tagged events are consumed by the `EventProcessor`
      val taggedEvent = Tagged(event, Set(s"$eventTag"))
      persist(taggedEvent) { _ =>
        applyEvent(event)
        log.info("persisted {}", taggedEvent)
      }

    case SendPortStatus  =>
        log.error("Cannot send port status of non-existing switch")
  }

  private def applyEvent(event: Event): Unit = {
    event match {
      case SwitchCreated(n) =>
        portStatus = createSwitch(n)

      case PortStatusSet(port, status) =>
        portStatus = portStatus.map(ps => ps + (port -> status))

      case PortStatusSent(portStatus: Map[Int, Boolean]) =>
        // no state update
        ()
    }
  }

  private def createSwitch(nPorts: Int): Option[Map[Int, Boolean]] = {
    val switch = (0 until nPorts).map(n => (n, false)).toMap
    println(s"Created switch: $switch")
    Some(switch)
  }

}
