package sample.sharding.kafka

case class KafkaMessage[A](key: String, message: A)
