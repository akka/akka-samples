package sample.sharding

import akka.actor._

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
