package sample.sharding

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Random

import akka.actor._
import akka.cluster.sharding._

object Counters {
  // Update a random counter
  case object UpdateCounter
}

class Counters extends Actor with ActorLogging {
  import Counters._
  
  private val extractEntityId: ShardRegion.ExtractEntityId = {
    case msg @ Counter.Increment(id) => (id.toString, msg)
    case msg @ Counter.Decrement(id) => (id.toString, msg)
  }

  private val numberOfShards = 100

  private val extractShardId: ShardRegion.ExtractShardId = {
    case Counter.Increment(id) => (id % numberOfShards).toString
    case Counter.Decrement(id) => (id % numberOfShards).toString
    // Needed if you want to use 'remember entities':
    //case ShardRegion.StartEntity(id) => (id.toLong % numberOfShards).toString
  }

  val counterRegion: ActorRef = ClusterSharding(context.system).start(
      typeName = "Counter",
      entityProps = Props[Counter],
      settings = ClusterShardingSettings(context.system),
      extractEntityId = extractEntityId,
      extractShardId = extractShardId)

  val random = new Random()
  val numberOfCounters = 50

  implicit val ec: ExecutionContext = context.dispatcher
  context.system.scheduler.schedule(10.seconds, 1.second, self, UpdateCounter)

  def receive = {
    case UpdateCounter => 
      val counterId = random.nextInt(numberOfCounters);
      val msg = 
        if (random.nextBoolean()) Counter.Increment(counterId) 
        else Counter.Decrement(counterId)
      log.info(s"Sending $msg");
      counterRegion ! msg 
  }
}
