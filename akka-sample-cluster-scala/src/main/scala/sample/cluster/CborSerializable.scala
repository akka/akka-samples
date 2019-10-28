package sample.cluster

/**
 * Marker trait to tell Akka to serialize messages into CBOR using Jackson for sending over the network
 * See application.conf where it is bound to a serializer.
 * For more details see the docs https://doc.akka.io/docs/akka/2.6/serialization-jackson.html
 */
trait CborSerializable
