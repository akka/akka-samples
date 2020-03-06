package sample.sharding.kafka

import akka.actor.ActorSystem
import akka.kafka.ConsumerSettings
import akka.util.Timeout
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, StringDeserializer}

import scala.jdk.CollectionConverters._
import scala.concurrent.duration._

case object ProcessorSettings {
  def apply(configLocation: String, system: ActorSystem): ProcessorSettings = {
    val config = system.settings.config.getConfig(configLocation)
    new ProcessorSettings(
      config.getString("bootstrap-servers"),
      config.getStringList("topics").asScala.toList,
      config.getString("group"),
      Timeout.create(config.getDuration("ask-timeout")),
      system: ActorSystem
    )
  }
}

final class ProcessorSettings(val bootstrapServers: String, val topics: List[String], val groupId: String, val askTimeout: Timeout, val system: ActorSystem) {
  def kafkaConsumerSettings(): ConsumerSettings[String, Array[Byte]] = {
    ConsumerSettings(system, new StringDeserializer, new ByteArrayDeserializer)
      .withBootstrapServers(bootstrapServers)
      .withGroupId(groupId)
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
      .withStopTimeout(0.seconds)

  }
}
