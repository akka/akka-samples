package sample.cluster.transformation

import java.util.concurrent.atomic.AtomicReference

import akka.actor.ExtendedActorSystem
import akka.serialization.BaseSerializer
import scalapb.GeneratedMessageCompanion

class ScalaPbSerializer(val system: ExtendedActorSystem) extends BaseSerializer {
  private val classToCompanionMapRef = new AtomicReference[Map[Class[_], GeneratedMessageCompanion[_]]](Map.empty)

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case e: scalapb.GeneratedMessage => e.toByteArray
    case _ => throw new IllegalArgumentException("Need a subclass of scalapb.GeneratedMessage")
  }

  override def includeManifest: Boolean = true

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
    manifest match {
      case Some(clazz) =>
        @scala.annotation.tailrec
        def messageCompanion(companion: GeneratedMessageCompanion[_] = null): GeneratedMessageCompanion[_] = {
          val classToCompanion = classToCompanionMapRef.get()
          classToCompanion.get(clazz) match {
            case Some(cachedCompanion) => cachedCompanion
            case None =>
              val uncachedCompanion =
                if (companion eq null) Class.forName(clazz.getName + "$", true, clazz.getClassLoader)
                  .getField("MODULE$").get((): Unit).asInstanceOf[GeneratedMessageCompanion[_]]
                else companion
              if (classToCompanionMapRef.compareAndSet(classToCompanion, classToCompanion.updated(clazz, uncachedCompanion)))
                uncachedCompanion
              else
                messageCompanion(uncachedCompanion)
          }
        }
        messageCompanion().parseFrom(bytes).asInstanceOf[AnyRef]
      case _ => throw new IllegalArgumentException("Need a ScalaPB companion class to be able to deserialize.")
    }
  }
}