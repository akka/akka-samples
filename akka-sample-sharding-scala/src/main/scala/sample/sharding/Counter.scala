package sample.sharding

import akka.actor._

/**
 * This is just an example: cluster sharding would be overkill for just keeping some counters,
 * but becomes useful when you have a collection of 'heavy' actors (in terms of processing or state)
 * so you need to distribute them across several nodes.
 */
object Counter {
  case class Increment(counterId: Int)
  case class Decrement(counterId: Int)
}
class Counter extends Actor with ActorLogging {
  import Counter._

  override def receive = counting(0)

  def counting(value: Int): Receive = {
    case Increment(id) =>
      log.info(s"Incrementing counter $id to ${value + 1}");
      context.become(counting(value + 1))
    case Decrement(id) =>
      log.info(s"Decrementing counter $id to ${value - 1}");
      context.become(counting(value - 1))
  }
}
