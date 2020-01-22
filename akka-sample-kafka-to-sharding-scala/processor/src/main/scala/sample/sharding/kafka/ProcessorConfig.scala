package sample.sharding.kafka

import com.typesafe.config.Config

case object ProcessorConfig {
  def apply(config: Config): ProcessorConfig =
    new ProcessorConfig(
      config.getString("bootstrap-servers"),
      config.getString("topic"),
      config.getString("group"),
      config.getInt("nr-partitions"))
}

final class ProcessorConfig(val bootstrapServers: String, val topic: String, val groupId: String, val nrPartitions: Int)
