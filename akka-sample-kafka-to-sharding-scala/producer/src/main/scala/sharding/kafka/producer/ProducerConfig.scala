package sharding.kafka.producer

import com.typesafe.config.Config

sealed trait Partitioning
case object Default extends Partitioning
case object Explicit extends Partitioning

object Partitioning {
  def valueOf(input: String): Partitioning = input.toLowerCase match {
    case "explicit" => Explicit
    case _          => Default
  }
}

case object ProducerConfig {
  def apply(config: Config): ProducerConfig =
    new ProducerConfig(
      config.getString("bootstrap-servers"),
      config.getString("topic"),
      Partitioning.valueOf(config.getString("partitioning")),
      config.getInt("nr-partitions"))
}

final class ProducerConfig(
    val bootstrapServers: String,
    val topic: String,
    val partitioning: Partitioning,
    val nrPartitions: Int)
