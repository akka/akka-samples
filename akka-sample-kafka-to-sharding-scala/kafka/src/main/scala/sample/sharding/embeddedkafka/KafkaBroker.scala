package sample.sharding.embeddedkafka

import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.slf4j.LoggerFactory

object KafkaBroker extends App with EmbeddedKafka {
  val log = LoggerFactory.getLogger(this.getClass)

  val port = 9092
  val topic = "user-events"
  val partitions = 128

  val config = EmbeddedKafkaConfig(kafkaPort = port)
  val server = EmbeddedKafka.start()(config)

  createCustomTopic(topic = topic, partitions = partitions)

  log.info(s"Kafka running on port '$port'")
  log.info(s"Topic '$topic' with '$partitions' partitions created")

  server.broker.awaitShutdown()
}
