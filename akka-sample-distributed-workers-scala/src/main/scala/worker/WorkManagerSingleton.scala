package worker

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.cluster.typed.ClusterSingleton

import scala.concurrent.duration._
import akka.cluster.typed._
import worker.WorkManager.Command

object WorkManagerSingleton {

  private val singletonName = "work-manager"
  private val singletonRole = "back-end"

  def init(system: ActorSystem[_]): ActorRef[Command] = {
    val workTimeout = system.settings.config.getDuration("distributed-workers.work-timeout").getSeconds.seconds

    ClusterSingleton(system).init(
      SingletonActor(WorkManager(workTimeout), singletonName)
        .withSettings(ClusterSingletonSettings(system).withRole(singletonRole)))
  }
}
