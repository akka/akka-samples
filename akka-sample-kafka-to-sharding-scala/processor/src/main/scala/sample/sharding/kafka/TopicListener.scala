package sample.sharding.kafka

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.external._
import akka.cluster.typed.Cluster
import akka.kafka.ConsumerRebalanceEvent
import akka.kafka.TopicPartitionsAssigned
import akka.kafka.TopicPartitionsRevoked
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey

import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success

object TopicListener {
  def apply(groupId: String, typeKey: EntityTypeKey[_]): Behavior[ConsumerRebalanceEvent] =
    Behaviors.setup { ctx =>
      import ctx.executionContext
      val shardAllocationClient = ExternalShardAllocation(ctx.system).clientFor(typeKey.name)
      ctx.system.scheduler.scheduleAtFixedRate(10.seconds, 20.seconds) { () =>
        shardAllocationClient.shardLocations().onComplete {
          case Success(shardLocations) =>
            ctx.log.info("Current shard locations {}", shardLocations.locations)
          case Failure(t) =>
            ctx.log.error("failed to get shard locations", t)
        }
      }
      val address = Cluster(ctx.system).selfMember.address
      Behaviors.receiveMessage[ConsumerRebalanceEvent] {
        case TopicPartitionsAssigned(sub, partitions) =>
          // TODO
          // - log all partitions assigned in one log line
          // - block for shard allocation to complete, add configurable timeout
          partitions.foreach(tp => {
            val shardId = s"$groupId-${tp.partition()}"
            ctx.log.info("Partition [{}] assigned to current node. Updating shard allocation", shardId)
            // kafka partition becomes the akka shard
            val done = shardAllocationClient.updateShardLocation(shardId, address)
            done.onComplete { result =>
              ctx.log.info("Result for updating shard {}: {}", shardId, result)
            }

          })
          Behaviors.same
        case TopicPartitionsRevoked(_, topicPartitions) =>
          ctx.log.info(
            "Partitions [{}] of group [{}] revoked from current node. New location will update shard allocation",
            topicPartitions.mkString(","),
            groupId)
          Behaviors.same
      }
    }
}
