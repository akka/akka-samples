package sample.sharding.kafka

import com.typesafe.config.Config
import scala.jdk.CollectionConverters._

case object ProcessorConfig {
  def apply(config: Config): ProcessorConfig =
    new ProcessorConfig(
      config.getString("bootstrap-servers"),
      config.getStringList("topics").asScala.toList,
      config.getString("group"))
}

final class ProcessorConfig(val bootstrapServers: String, val topics: List[String], val groupId: String)
