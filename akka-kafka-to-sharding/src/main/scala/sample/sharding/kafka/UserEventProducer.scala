package sample.sharding.kafka

import akka.Done
import akka.actor.ActorSystem
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream.scaladsl.Source
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.IntegerSerializer
import org.apache.kafka.common.serialization.StringSerializer

import scala.concurrent.Future
import scala.concurrent.duration._

object UserEventProducer extends App {

  implicit val system: ActorSystem = ActorSystem()

  val config = system.settings.config.getConfig("akka.kafka.producer")

  val NrPartitions = 128

  val producerSettings: ProducerSettings[Integer, String] =
    ProducerSettings(config, new IntegerSerializer, new StringSerializer)
      .withBootstrapServers(UserEventsKafkaProcessor.KafkaBootstrapServers)

  val done: Future[Done] =
    Source.tick(100.millis, 100.millis, "tick")
      .map(value => {
                
        new ProducerRecord[Integer, String](UserEventsKafkaProcessor.Topic, value)
      })
      .runWith(Producer.plainSink(producerSettings))

}
