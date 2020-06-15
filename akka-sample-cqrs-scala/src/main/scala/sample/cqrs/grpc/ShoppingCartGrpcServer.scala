package sample.cqrs.grpc

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.HttpConnectionContext
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import org.slf4j.LoggerFactory
import sample.cqrs.EventProcessorSettings

class ShoppingCartGrpcServer(system: ActorSystem[_], port: Int, eventProcessorSettings: EventProcessorSettings) {
  private val log = LoggerFactory.getLogger(getClass)

  def start(): Future[Http.ServerBinding] = {
    import akka.actor.typed.scaladsl.adapter._
    implicit val classicSystem: akka.actor.ActorSystem = system.toClassic
    implicit val ec: ExecutionContext = system.executionContext

    val service: HttpRequest => Future[HttpResponse] =
      ShoppingCartServiceHandler(new ShoppingCartServiceImpl(system, eventProcessorSettings))

    val bound =
      Http().bindAndHandleAsync(service, interface = "127.0.0.1", port, connectionContext = HttpConnectionContext())

    bound.foreach { binding =>
      log.info("gRPC server bound to: {}", binding.localAddress)
    }

    bound
  }
}
