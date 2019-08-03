/**
 * Copyright (C) 2009-2019 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.cluster.client.grpc

import akka.actor.ActorSystem
import akka.annotation.InternalApi
import akka.serialization.SerializationExtension
import akka.serialization.Serializers
import com.google.protobuf.ByteString

/**
 * INTERNAL API
 */
@InternalApi private[akka] class ClusterClientSerialization(system: ActorSystem) {

  private val serialization = SerializationExtension(system)

  def serializePayload(message: Any): Payload = {

    val msg = message.asInstanceOf[AnyRef]
    val msgSerializer = serialization.findSerializerFor(msg)
    val manifest = Serializers.manifestFor(msgSerializer, msg)
    val serializerId = msgSerializer.identifier

    Payload(ByteString.copyFrom(serialization.serialize(msg).get), serializerId, manifest)
  }

  def deserializePayload(payload: Payload): AnyRef = {
    serialization.deserialize(payload.enclosedMessage.toByteArray, payload.serializerId, payload.messageManifest).get
  }

}
