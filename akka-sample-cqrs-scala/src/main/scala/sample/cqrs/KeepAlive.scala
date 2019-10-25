package sample.cqrs

import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.cluster.typed.ClusterSingleton
import akka.cluster.typed.ClusterSingletonSettings
import akka.cluster.typed.SingletonActor

/**
 * General purpose event processor infrastructure. Not specific to the ShoppingCart domain.
 */
object KeepAlive {
  case object Probe

  def init(system: ActorSystem[_], eventProcessorEntityKey: EntityTypeKey[EventProcessor.Ping.type]): Unit = {
    val settings = EventProcessorSettings(system)
    ClusterSingleton(system).init(
      SingletonActor(KeepAlive(settings, eventProcessorEntityKey), s"keepAlive-${settings.id}")
        .withSettings(ClusterSingletonSettings(system).withRole("read-model")))
  }

  def apply(
      settings: EventProcessorSettings,
      eventProcessorEntityKey: EntityTypeKey[EventProcessor.Ping.type]): Behavior[Probe.type] = {
    Behaviors.setup { context =>
      Behaviors.withTimers { timers =>
        val sharding = ClusterSharding(context.system)

        timers.startTimerWithFixedDelay(Probe, Probe, settings.keepAliveInterval)

        Behaviors.receiveMessage { probe =>
          for (n <- 0 until settings.parallelism) {
            val eventProcessorId: String = s"${settings.tagPrefix}-$n"
            sharding.entityRefFor(eventProcessorEntityKey, eventProcessorId) ! EventProcessor.Ping
          }
          Behaviors.same
        }
      }
    }

  }
}
