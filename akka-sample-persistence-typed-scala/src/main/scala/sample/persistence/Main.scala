package sample.persistence

import scala.concurrent.duration._

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout

object Main extends App {
implicit val timeout: Timeout = 5.seconds

    case class Ack(description: String)

    // First test: start a single persistent actor.
    // TODO: start them dynamically, actually do something with responses beyond printing
    // You can 'manually' test things are actually persisted by changing the commands and looking
    // at this
    val rootBehavior = Behaviors.setup[Ack](context => {
        val persistentActor = context.spawn(ShoppingCart.behavior("test"), "test")
        context.ask(persistentActor)((self: ActorRef[ShoppingCart.Result]) => ShoppingCart.UpdateItem("foo", 32, self)) {
            case r => Ack(r.toString)
        }
        context.ask(persistentActor)((self: ActorRef[ShoppingCart.Result]) => ShoppingCart.UpdateItem("bar", 12, self)) {
            case r => Ack(r.toString)
        }
        context.ask(persistentActor)((self: ActorRef[ShoppingCart.State]) => ShoppingCart.Get(self)) {
            case response =>
                Ack(response.toString)
        }
        Behaviors.receive((ctx, ack) => {
            println(ack.description)
            Behaviors.same
        })
    })
    
    ActorSystem(rootBehavior, "sample-persistence")
}