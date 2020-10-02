package akka.projection.testing

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat

object TestRoutes {
  case class RunTest(name: String, nrActors: Long, messagesPerActor: Long)
  case class TestResult(pass: Boolean, expected: Long, got: Long)

  implicit val runTestFormat: RootJsonFormat[RunTest] = jsonFormat3(RunTest)
  implicit val testResultFormat: RootJsonFormat[TestResult] = jsonFormat3(TestResult)
}

//class TestRoutes()(implicit val system: ActorSystem[_]) {
//  import TestRoutes._
//  val route: Route = path("test") {
//    post {
//      entity(as[RunTest]) { runTest =>
//
//
//      }
//    }
//  }
//
//}
