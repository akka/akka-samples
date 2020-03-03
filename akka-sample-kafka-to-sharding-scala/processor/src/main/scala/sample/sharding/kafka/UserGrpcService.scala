package sample.sharding.kafka

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.delivery.ConsumerController.SequencedMessage
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import sample.sharding.kafka.UserEvents.GetRunningTotal
import sample.sharding.kafka.UserEvents.RunningTotal

import scala.concurrent.Future
import scala.concurrent.duration._
import akka.cluster.sharding.typed.ShardingEnvelope

class UserGrpcService(system: ActorSystem[_]) extends UserService {

  implicit val timeout = Timeout(5.seconds)
  implicit val sched = system.scheduler
  implicit val ec = system.executionContext

 override def userStats(in: UserStatsRequest): Future[UserStatsResponse] = {
   // shardRegion
   //   .ask[RunningTotal](replyTo => GetRunningTotal(in.id, replyTo))
   //   .map(runningTotal => UserStatsResponse(in.id, runningTotal.totalPurchases, runningTotal.amountSpent))
    ???
  }

}
