package sample.sharding.kafka

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.AskPattern._
import akka.cluster.sharding.typed.ShardingMessageExtractor
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import sample.sharding.kafka.UserEvents.GetRunningTotal
import sample.sharding.kafka.UserEvents.RunningTotal

import scala.concurrent.Future
import scala.concurrent.duration._

class UserGrpcService(system: ActorSystem[_], extractor: ShardingMessageExtractor[UserEvents.Message, UserEvents.Message]) extends UserService {

  implicit val timeout = Timeout(5.seconds)
  implicit val sched = system.scheduler
  implicit val ec = system.executionContext

  private val processorSettings = ProcessorConfig(ConfigFactory.load().getConfig("kafka-to-sharding-processor"))
  private val shardRegion: ActorRef[UserEvents.UserQuery] = UserEvents.querySide(system, extractor, processorSettings.groupId)

  override def userStats(in: UserStatsRequest): Future[UserStatsResponse] = {
    shardRegion
      .ask[RunningTotal](replyTo => GetRunningTotal(in.id, replyTo))
      .map(runningTotal => UserStatsResponse(in.id, runningTotal.totalPurchases, runningTotal.amountSpent))
  }
}
