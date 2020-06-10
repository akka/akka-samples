package sample.cqrs

import akka.actor.typed.ActorSystem
import com.typesafe.config.Config

object EventProcessorSettings {

  def apply(system: ActorSystem[_]): EventProcessorSettings = {
    apply(system.settings.config.getConfig("event-processor"))
  }

  def apply(config: Config): EventProcessorSettings = {
    val tagPrefix: String = config.getString("tag-prefix")
    val parallelism: Int = config.getInt("parallelism")
    EventProcessorSettings(tagPrefix, parallelism)
  }
}

final case class EventProcessorSettings(tagPrefix: String, parallelism: Int)
