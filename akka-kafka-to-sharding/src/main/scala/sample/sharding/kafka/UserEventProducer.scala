package sample.sharding.kafka

import akka.Done
import akka.actor.ActorSystem
import akka.event.Logging
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream.scaladsl.Source
import com.typesafe.config.ConfigFactory
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.IntegerSerializer
import org.apache.kafka.common.serialization.StringSerializer

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Random

// TODO move this to another sub project and only share protobuf between the two
object UserEventProducer extends App {

  implicit val system: ActorSystem = ActorSystem(
    "UserEventProducer",
    ConfigFactory.parseString("""
      akka.actor.provider = "local" 
     """.stripMargin).resolve())

  val log = Logging(system, "UserEventProducer")

  val config = system.settings.config.getConfig("akka.kafka.producer")

  val NrPartitions = 128

  def userIdToPartition(userId: Int) = math.abs(userId % NrPartitions)

  val producerSettings: ProducerSettings[Integer, String] =
    ProducerSettings(config, new IntegerSerializer, new StringSerializer)
      .withBootstrapServers(UserEventsKafkaProcessor.KafkaBootstrapServers)

  val done: Future[Done] =
    Source
      .tick(1000.millis, 1000.millis, "tick")
      .map(value => {
        val randomUserId = Random.nextInt()
        val partition = userIdToPartition(randomUserId)
        log.info("Sending message to user {} partition {}", randomUserId, partition)
        new ProducerRecord[Integer, String](
          UserEventsKafkaProcessor.Topic,
          partition,
          randomUserId, // Message key, used on the other side. Alternatively the userId could be in the message.
          s"message for user id ${randomUserId}")
      })
      .runWith(Producer.plainSink(producerSettings))

}
