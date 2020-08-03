package sample.cqrs

import scala.concurrent.Future
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.pattern.StatusReply
import akka.util.Timeout

object ShoppingCartRoutes {
  final case class AddItem(cartId: String, itemId: String, quantity: Int)
  final case class UpdateItem(cartId: String, itemId: String, quantity: Int)
}

class ShoppingCartRoutes()(implicit system: ActorSystem[_]) {

  implicit private val timeout: Timeout =
    Timeout.create(system.settings.config.getDuration("shopping.askTimeout"))
  private val sharding = ClusterSharding(system)

  import ShoppingCartRoutes._
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import akka.http.scaladsl.server.Directives._
  import JsonFormats._

  val shopping: Route =
    pathPrefix("shopping") {
      pathPrefix("carts") {
        concat(
          post {
            entity(as[AddItem]) {
              data =>
                val entityRef =
                  sharding.entityRefFor(ShoppingCart.EntityKey, data.cartId)
                val reply: Future[StatusReply[ShoppingCart.Summary]] =
                  entityRef.ask(ShoppingCart.AddItem(data.itemId, data.quantity, _))
                onSuccess(reply) {
                  case StatusReply.Success(summary: ShoppingCart.Summary) =>
                    complete(StatusCodes.OK -> summary)
                  case StatusReply.Error(reason) =>
                    complete(StatusCodes.BadRequest -> reason)
                }
            }
          },
          put {
            entity(as[UpdateItem]) {
              data =>
                val entityRef =
                  sharding.entityRefFor(ShoppingCart.EntityKey, data.cartId)

                def command(replyTo: ActorRef[StatusReply[ShoppingCart.Summary]]) =
                  if (data.quantity == 0)
                    ShoppingCart.RemoveItem(data.itemId, replyTo)
                  else
                    ShoppingCart.AdjustItemQuantity(data.itemId, data.quantity, replyTo)

                val reply: Future[StatusReply[ShoppingCart.Summary]] =
                  entityRef.ask(command(_))
                onSuccess(reply) {
                  case StatusReply.Success(summary: ShoppingCart.Summary) =>
                    complete(StatusCodes.OK -> summary)
                  case StatusReply.Error(reason) =>
                    complete(StatusCodes.BadRequest -> reason)
                }
            }
          },
          pathPrefix(Segment) { cartId =>
            concat(get {
              val entityRef =
                sharding.entityRefFor(ShoppingCart.EntityKey, cartId)
              onSuccess(entityRef.ask(ShoppingCart.Get)) { summary =>
                if (summary.items.isEmpty) complete(StatusCodes.NotFound)
                else complete(summary)
              }
            }, path("checkout") {
              post {
                val entityRef =
                  sharding.entityRefFor(ShoppingCart.EntityKey, cartId)
                val reply: Future[StatusReply[ShoppingCart.Summary]] =
                  entityRef.ask(ShoppingCart.Checkout(_))
                onSuccess(reply) {
                  case StatusReply.Success(summary: ShoppingCart.Summary) =>
                    complete(StatusCodes.OK -> summary)
                  case StatusReply.Error(reason) =>
                    complete(StatusCodes.BadRequest -> reason)
                }
              }
            })
          })
      }
    }

}

object JsonFormats {

  import spray.json.RootJsonFormat
  // import the default encoders for primitive types (Int, String, Lists etc)
  import spray.json.DefaultJsonProtocol._

  implicit val summaryFormat: RootJsonFormat[ShoppingCart.Summary] =
    jsonFormat2(ShoppingCart.Summary)
  implicit val addItemFormat: RootJsonFormat[ShoppingCartRoutes.AddItem] =
    jsonFormat3(ShoppingCartRoutes.AddItem)
  implicit val updateItemFormat: RootJsonFormat[ShoppingCartRoutes.UpdateItem] =
    jsonFormat3(ShoppingCartRoutes.UpdateItem)

}
