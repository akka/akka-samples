package sample.cluster.stats

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

import scala.concurrent.duration._

//#worker
object StatsWorker {

  trait Command
  final case class Process(word: String, replyTo: ActorRef[Processed]) extends Command
  private case object EvictCache extends Command

  final case class Processed(word: String, length: Int)

  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    Behaviors.withTimers { timers =>
      ctx.log.info("Worker starting up")
      timers.startTimerWithFixedDelay(EvictCache, EvictCache, 30.seconds)

      withCache(Map.empty)
    }
  }

  private def withCache(cache: Map[String, Int]): Behavior[Command] = Behaviors.receiveMessage {
    case Process(word, replyTo) =>
      cache.get(word) match {
        case Some(length) =>
          replyTo ! Processed(word, length)
          Behaviors.same
        case None =>
          val length = word.length
          val updatedCache = cache + (word -> length)
          replyTo ! Processed(word, length)
          withCache(updatedCache)
      }
    case EvictCache =>
      withCache(Map.empty)
  }
}
//#worker