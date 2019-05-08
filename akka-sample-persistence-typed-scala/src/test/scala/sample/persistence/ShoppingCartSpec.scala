package sample.persistence

import akka.actor.testkit.typed.scaladsl.ActorTestKit

import org.scalatest._

class AsyncTestingExampleSpec extends WordSpec with BeforeAndAfterAll with Matchers {
    val testKit = ActorTestKit()

    "The Shopping Cart" should {
        "keep its state" in {
            
        }
    }
}