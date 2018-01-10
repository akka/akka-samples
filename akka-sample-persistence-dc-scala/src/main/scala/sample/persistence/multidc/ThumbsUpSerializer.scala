package sample.persistence.multidc

import java.io.NotSerializableException

import akka.serialization.BaseSerializer
import akka.serialization.SerializerWithStringManifest
import sample.persistence.multidc.protobuf.ThumbsUpMessages
import scala.collection.JavaConverters._

class ThumbsUpSerializer(val system: akka.actor.ExtendedActorSystem)
  extends SerializerWithStringManifest with BaseSerializer {

  private val StateManifest = "a"
  private val GiveThumbsUpManifest = "b"
  private val GetCountManifest = "c"
  private val GetUsersManifest = "d"
  private val GaveThumbsUpManifest = "e"

  override def manifest(o: AnyRef): String = o match {
    case _: ThumbsUpCounter.GiveThumbsUp ⇒ GiveThumbsUpManifest
    case _: ThumbsUpCounter.GaveThumbsUp  ⇒ GaveThumbsUpManifest
    case _: ThumbsUpCounter.State   ⇒ StateManifest
    case _: ThumbsUpCounter.GetCount     ⇒ GetCountManifest
    case _: ThumbsUpCounter.GetUsers ⇒ GetUsersManifest

    case _ ⇒
      throw new IllegalArgumentException(s"Can't serialize object of type ${o.getClass} in [${getClass.getName}]")
  }

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case a: ThumbsUpCounter.GiveThumbsUp ⇒ giveThumbsUpToBinary(a)
    case a: ThumbsUpCounter.GaveThumbsUp  ⇒ gaveThumbsUpToBinary(a)
    case a: ThumbsUpCounter.State   ⇒ stateToBinary(a)
    case a: ThumbsUpCounter.GetCount     ⇒ getCountToBinary(a)
    case a: ThumbsUpCounter.GetUsers ⇒ getUsersToBinary(a)

    case _ ⇒
      throw new IllegalArgumentException(s"Cannot serialize object of type [${o.getClass.getName}]")
  }

  private def stateToBinary(a: ThumbsUpCounter.State): Array[Byte] = {
    ThumbsUpMessages.State.newBuilder()
     .addAllUsers(a.users.asJava)
     .build().toByteArray()
  }

  private def giveThumbsUpToBinary(a: ThumbsUpCounter.GiveThumbsUp): Array[Byte] = {
    ThumbsUpMessages.GiveThumbsUp.newBuilder()
      .setResourceId(a.resourceId)
      .setUserId(a.userId)
      .build().toByteArray()
  }

  private def getCountToBinary(a: ThumbsUpCounter.GetCount): Array[Byte] = {
    ThumbsUpMessages.GetCount.newBuilder()
      .setResourceId(a.resourceId)
      .build().toByteArray()
  }

  private def getUsersToBinary(a: ThumbsUpCounter.GetUsers): Array[Byte] = {
    ThumbsUpMessages.GetUsers.newBuilder()
      .setResourceId(a.resourceId)
      .build().toByteArray()
  }

  private def gaveThumbsUpToBinary(a: ThumbsUpCounter.GaveThumbsUp): Array[Byte] = {
    ThumbsUpMessages.GaveThumbsUp.newBuilder()
      .setUserId(a.userId)
      .build().toByteArray()
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = manifest match {
    case StateManifest   ⇒ stateFromBinary(bytes)
    case GiveThumbsUpManifest ⇒ giveThumbsUpFromBinary(bytes)
    case GaveThumbsUpManifest     ⇒ gaveThumbsUpFromBinary(bytes)
    case GetCountManifest ⇒ getCountFromBinary(bytes)
    case GetUsersManifest  ⇒ getUsersFromBinary(bytes)

    case _ ⇒
      throw new NotSerializableException(
        s"Unimplemented deserialization of message with manifest [$manifest] in [${getClass.getName}]")
  }

  private def stateFromBinary(bytes: Array[Byte]): ThumbsUpCounter.State = {
    val a = ThumbsUpMessages.State.parseFrom(bytes)
    ThumbsUpCounter.State(a.getUsersList.asScala.toSet)
  }

  private def giveThumbsUpFromBinary(bytes: Array[Byte]): ThumbsUpCounter.GiveThumbsUp = {
    val a = ThumbsUpMessages.GiveThumbsUp.parseFrom(bytes)
    ThumbsUpCounter.GiveThumbsUp(a.getResourceId, a.getUserId)
  }

  private def getCountFromBinary(bytes: Array[Byte]): ThumbsUpCounter.GetCount = {
    val a = ThumbsUpMessages.GetCount.parseFrom(bytes)
    ThumbsUpCounter.GetCount(a.getResourceId)
  }

  private def getUsersFromBinary(bytes: Array[Byte]): ThumbsUpCounter.GetUsers = {
    val a = ThumbsUpMessages.GetUsers.parseFrom(bytes)
    ThumbsUpCounter.GetUsers(a.getResourceId)
  }

  private def gaveThumbsUpFromBinary(bytes: Array[Byte]): ThumbsUpCounter.GaveThumbsUp = {
    val a = ThumbsUpMessages.GaveThumbsUp.parseFrom(bytes)
    ThumbsUpCounter.GaveThumbsUp(a.getUserId)
  }
}
