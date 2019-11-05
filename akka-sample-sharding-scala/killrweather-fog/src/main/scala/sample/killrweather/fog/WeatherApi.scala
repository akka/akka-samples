package sample.killrweather.fog

import scala.util.{Failure, Success}

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.client.RequestBuilding.{Delete, Post, Put}
import akka.http.scaladsl.model.{HttpRequest, StatusCodes}
import akka.stream.SystemMaterializer

/**
  * Posts buffered edges data to the cluster.
  *
  * Find out more about Akka HTTP's rich options for marshalling, unmarshalling and json
  * support in the wild at https://doc.akka.io/docs/akka-http/current/common/index.html.
  */
private[fog] object WeatherApi {

  sealed trait Command
  final case class Add(wsid: WeatherStation.WmoId, replyTo: ActorRef[WeatherStation.Start.type]) extends Command
  case object Remove extends Command
  final case class ProcessSuccess(msg: String) extends Command
  final case class ProcessFailure(e: Throwable) extends Command

  /** Actual weather data event comprises many more data points.
   *
   * @param wsid maps to the Cluster Sharding `EntityTypeKey`
   * @param eventTime time collected
   * @param temperature Air temperature (degrees Celsius)
   */
  final case class Data(wsid: String, eventTime: Long, temperature: Double) extends Command

  /** If you wanted to return results to the sending Station you could
    * add the ref to the constructor:
    * {{{
    *     def apply(stationId: Int, port: Int, reply: ActorRef[Station.Response]): Behavior[Command] =
    * }}}
    */
  def apply(host: String, port: Int, ws: WeatherStation.WmoId): Behavior[Command] =
    Behaviors.setup { context =>

      import akka.actor.typed.scaladsl.adapter._
      val http = Http(context.system.toClassic)
      val baseUri = s"http://$host:$port/weather"

      import context.executionContext
      implicit val mat =
        SystemMaterializer(context.system.toClassic).materializer

      import JsonFormats._
      // This import makes the 'formats' above available to the Akka HTTP
      // marshalling infrastructure used when constructing the Post below:
      import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

      def simpleSingleRequest(request: HttpRequest): Behavior[Command] = {
        context.pipeToSelf(
          http
            .singleRequest(request)
            .flatMap(res => Unmarshal(res).to[String])) {
              case Success(s) => ProcessSuccess(s)
              case Failure(e) => ProcessFailure(e)
            }

        Behaviors.same
      }

      Behaviors.receiveMessage {
        case Add(ws, replyTo) =>
          http
            .singleRequest(Put(s"$baseUri/${ws.id}", ws.id))
            .foreach(res => Unmarshal(res).to[String].onComplete {
                case Success(v) =>
                  if (res.status == StatusCodes.Created && v == ws.id)
                    replyTo ! WeatherStation.Start
                case Failure(e) => ProcessFailure(e)
              })

          Behaviors.same

        case data: Data =>
          simpleSingleRequest(Post(s"$baseUri/${ws.id}", data))

        case ProcessSuccess(msg) =>
          context.log.debug(msg)
          Behaviors.same

        case ProcessFailure(e) =>
          context.log.error("Useful failure context.", e)
          Behaviors.same

        case Remove =>
          simpleSingleRequest(Delete(s"$baseUri/${ws.id}"))

      }
    }
}

