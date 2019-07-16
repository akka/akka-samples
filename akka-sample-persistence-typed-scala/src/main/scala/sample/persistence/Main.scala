package sample.persistence

import scala.concurrent.duration._
import scala.util.Success

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout
import akka.actor.Scheduler

object Main extends App {
implicit val timeout: Timeout = 5.seconds

    sealed trait Result
    case object Ok extends Result
    case class Failed(cause: Any) extends Result

    // First test: start a single persistent actor.
    // TODO: start them dynamically, actually do something with responses beyond printing
    // You can 'manually' test things are actually persisted by changing the commands and looking
    // at this
    val rootBehavior = Behaviors.setup[Result](context => {
        val persistentActor = context.spawn(ShoppingCart.behavior("test"), "test")
        context.ask(persistentActor)(ShoppingCart.UpdateItem("foo", 32, _: ActorRef[ShoppingCart.Result])) {
            case Success(ShoppingCart.OK) => Ok
            case e => Failed(e)
        }
        context.ask(persistentActor)(ShoppingCart.UpdateItem("bar", 12, _: ActorRef[ShoppingCart.Result])) {
            case Success(ShoppingCart.OK) => Ok
            case e => Failed(e)
        }
        context.ask(persistentActor)(ShoppingCart.Get) {
            case Success(ShoppingCart.State(items, _)) => Ok
            case e => Failed(e)
        }
        Behaviors.receive((ctx, ack) => {
            println(ack.toString)
            Behaviors.same
        })
    })
    
    ActorSystem(rootBehavior, "sample-persistence")
}
