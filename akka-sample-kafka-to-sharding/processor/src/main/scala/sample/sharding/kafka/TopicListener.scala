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
  def apply(typeKey: EntityTypeKey[_]): Behavior[ConsumerRebalanceEvent] =
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
        case TopicPartitionsAssigned(_, partitions) =>
          partitions.foreach(partition => {
            ctx.log.info("Partition [{}] assigned to current node. Updating shard allocation", partition.partition())
            // kafka partition becomes the akka shard
            val done = shardAllocationClient.updateShardLocation(partition.partition().toString, address)
            done.onComplete { result =>
              ctx.log.info("Result for updating shard {}: {}", partition, result)
            }

          })
          Behaviors.same
        case TopicPartitionsRevoked(_, topicPartitions) =>
          ctx.log.info(
            "Partitions [{}] revoked from current node. New location will update shard allocation",
            topicPartitions.mkString(","))
          Behaviors.same
      }
    }
}
