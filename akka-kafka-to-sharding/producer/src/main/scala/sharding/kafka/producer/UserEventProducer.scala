package sharding.kafka.producer

import akka.Done
import akka.actor.ActorSystem
import akka.event.Logging
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream.scaladsl.Source
import com.typesafe.config.ConfigFactory
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.common.utils.Utils

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Random

object UserEventProducer extends App {

  implicit val system: ActorSystem = ActorSystem(
    "UserEventProducer",
    ConfigFactory.parseString("""
      akka.actor.provider = "local" 
     """.stripMargin).withFallback(ConfigFactory.load()).resolve())

  val log = Logging(system, "UserEventProducer")

  val config = system.settings.config.getConfig("akka.kafka.producer")

  val producerConfig = ProducerConfig(system.settings.config.getConfig("kafka-to-sharding-producer"))

  val producerSettings: ProducerSettings[String, String] =
    ProducerSettings(config, new StringSerializer, new StringSerializer)
      .withBootstrapServers(producerConfig.bootstrapServers)

  val done: Future[Done] =
    Source
      .tick(1000.millis, 1000.millis, "tick")
      .map(value => {
        val randomEntityId = Random.nextInt().toString
        log.info("Sending message to user {}", randomEntityId)
        producerRecord(randomEntityId, s"message for user id ${randomEntityId}")

      })
      .runWith(Producer.plainSink(producerSettings))

  def producerRecord(entityId: String, message: String): ProducerRecord[String, String] = {
    producerConfig.partitioning match {
      case Default =>
        // rely on the default kafka partitioner to hash the key and distribute among shards
        // the logic of the default partitionor must be replicated in MessageExtractor entityId -> shardId function
        new ProducerRecord[String, String](producerConfig.topic, entityId, message)
      case Explicit =>
        // this logic MUST be replicated in the MessageExtractor entityId -> shardId function!
        val shardAndPartition = (Utils.toPositive(Utils.murmur2(entityId.getBytes())) % producerConfig.nrPartitions)
        new ProducerRecord[String, String](producerConfig.topic, shardAndPartition, entityId, message)
    }
  }

}
