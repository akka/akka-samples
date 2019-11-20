package sample.sharding.kafka

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.dynamic.DynamicShardAllocation
import akka.cluster.typed.Cluster
import akka.kafka.ConsumerRebalanceEvent
import akka.kafka.TopicPartitionsAssigned
import akka.kafka.TopicPartitionsRevoked
import akka.actor.typed.scaladsl.adapter._
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey

object TopicListener {
  def apply(typeKey: EntityTypeKey[_], partitionToShard: Int => String = _.toString): Behavior[ConsumerRebalanceEvent] =
    Behaviors.setup { ctx =>
      val shardAllocationClient = DynamicShardAllocation(ctx.system.toClassic).clientFor(typeKey.name)
      val address = Cluster(ctx.system).selfMember.address
      Behaviors.receiveMessage[ConsumerRebalanceEvent] {
        case TopicPartitionsAssigned(_, partitions) =>
          partitions.foreach(partition => {
            ctx.log.info("Partition [{}] assigned to current node. Updating shard allocation", partition.partition())
            // TODO deal with failure? just retry or pipe to self and do something?
            shardAllocationClient.updateShardLocation(partitionToShard(partition.partition()), address)
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
