package sample.cqrs

import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.util.Timeout

object ShoppingCartRoutes {
  final case class AddItem(cartId: String, itemId: String, quantity: Int)
  final case class UpdateItem(cartId: String, itemId: String, quantity: Int)
  final case class Checkout(cartId: String)
}

class ShoppingCartRoutes()(implicit system: ActorSystem[_]) {

  implicit private val timeout: Timeout = 5.seconds
  private val sharding = ClusterSharding(system)

  import ShoppingCartRoutes._
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import akka.http.scaladsl.server.Directives._
  import JsonFormats._

  val shopping: Route =
    pathPrefix("shopping-carts") {
      concat(
        post {
          entity(as[AddItem]) {
            data =>
              val entityRef = sharding.entityRefFor(ShoppingCart.EntityKey, data.cartId)
              val reply: Future[ShoppingCart.Confirmation] =
                entityRef.ask(ShoppingCart.AddItem(data.itemId, data.quantity, _))
              onSuccess(reply) {
                case ShoppingCart.Accepted(_) =>
                  complete(
                    StatusCodes.Accepted -> s"added item ${data.itemId}: ${data.quantity} to cart ${data.cartId}")
                case ShoppingCart.Rejected(reason) =>
                  complete(StatusCodes.BadRequest, reason)
              }
          }
        },
        put {
          entity(as[UpdateItem]) {
            data =>
              val entityRef = sharding.entityRefFor(ShoppingCart.EntityKey, data.cartId)
              def command(replyTo: ActorRef[ShoppingCart.Confirmation]) =
                if (data.quantity == 0) ShoppingCart.RemoveItem(data.itemId, replyTo)
                else ShoppingCart.AdjustItemQuantity(data.itemId, data.quantity, replyTo)
              val reply: Future[ShoppingCart.Confirmation] = entityRef.ask(command(_))
              onSuccess(reply) {
                case ShoppingCart.Accepted(_) =>
                  complete(StatusCodes.Accepted -> s"updated cart ${data.cartId}")
                case ShoppingCart.Rejected(reason) =>
                  complete(StatusCodes.BadRequest, reason)
              }
          }
        },
        put {
          entity(as[Checkout]) { data =>
            val entityRef = sharding.entityRefFor(ShoppingCart.EntityKey, data.cartId)
            val reply: Future[ShoppingCart.Confirmation] = entityRef.ask(ShoppingCart.Checkout(_))
            onSuccess(reply) {
              case ShoppingCart.Accepted(_) =>
                complete(StatusCodes.Accepted -> s"Checked out cart ${data.cartId}")
              case ShoppingCart.Rejected(reason) =>
                complete(StatusCodes.BadRequest, reason)
            }
          }
        },
        path(Segment) { cartId =>
          get {
            val entityRef = sharding.entityRefFor(ShoppingCart.EntityKey, cartId)
            onSuccess(entityRef.ask(ShoppingCart.Get)) { summary =>
              if (summary.items.isEmpty) complete(StatusCodes.NotFound)
              else complete(summary)
            }
          }
        })
    }

}

object JsonFormats {

  import spray.json.RootJsonFormat
  // import the default encoders for primitive types (Int, String, Lists etc)
  import spray.json.DefaultJsonProtocol._

  implicit val summaryFormat: RootJsonFormat[ShoppingCart.Summary] = jsonFormat2(ShoppingCart.Summary)
  implicit val addItemFormat: RootJsonFormat[ShoppingCartRoutes.AddItem] = jsonFormat3(ShoppingCartRoutes.AddItem)
  implicit val updateItemFormat: RootJsonFormat[ShoppingCartRoutes.UpdateItem] = jsonFormat3(
    ShoppingCartRoutes.UpdateItem)
  implicit val checkoutFormat: RootJsonFormat[ShoppingCartRoutes.Checkout] = jsonFormat1(ShoppingCartRoutes.Checkout)

}
