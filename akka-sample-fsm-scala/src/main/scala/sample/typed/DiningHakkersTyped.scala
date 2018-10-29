package sample.typed

import akka.NotUsed
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}

import scala.concurrent.duration._

// Akka adaptation of
// http://www.dalnefre.com/wp/2010/08/dining-philosophers-in-humus/

/*
* Some messages for the chopstick
*/
sealed trait ChopstickMessage
final case class Take(ref: ActorRef[ChopstickAnswer]) extends ChopstickMessage
final case class Put(ref: ActorRef[ChopstickAnswer]) extends ChopstickMessage

sealed trait ChopstickAnswer
final case class Taken(chopstick: ActorRef[ChopstickMessage]) extends ChopstickAnswer
final case class Busy(chopstick: ActorRef[ChopstickMessage]) extends ChopstickAnswer

/*
* A Chopstick is an actor, it can be taken, and put back
*/
object Chopstick {

  //When a Chopstick is taken by a hakker
  //It will refuse to be taken by other hakkers
  //But the owning hakker can put it back
  def takenBy(hakker: ActorRef[ChopstickAnswer]): Behavior[ChopstickMessage] = {
    Behaviors.receivePartial {
      case (ctx, Take(otherHakker)) =>
        otherHakker ! Busy(ctx.self)
        Behaviors.same
      case (ctx, Put(`hakker`)) =>
        available
    }
  }

  //When a Chopstick is available, it can be taken by a hakker
  lazy val available: Behavior[ChopstickMessage] = {
    Behaviors.receivePartial {
      case (ctx, Take(hakker)) =>
        hakker ! Taken(ctx.self)
        takenBy(hakker)
    }
  }
}

/**
  * Some fsm hakker messages
  */
sealed trait HakkerMessage
case object Think extends HakkerMessage
case object Eat extends HakkerMessage

/*
* A hakker is an awesome dude or dudette who either thinks about hacking or has to eat ;-)
*/
class Hakker(name: String, left: ActorRef[ChopstickMessage], right: ActorRef[ChopstickMessage]) {

  final case class ChopstickAnswerAdaptor(msg: ChopstickAnswer) extends HakkerMessage

  lazy val waiting: Behavior[HakkerMessage] =
    Behaviors.receivePartial {
      case (ctx, Think) =>
        println(s"$name starts to think")
        startThinking(ctx, 5.seconds)
    }

  //When a hakker is thinking it can become hungry
  //and try to pick up its chopsticks and eat
  lazy val thinking: Behavior[HakkerMessage] =
    Behaviors.setup { ctx =>
      val adapter = ctx.messageAdapter(ChopstickAnswerAdaptor)
      Behaviors.receiveMessagePartial {
        case Eat =>
          left ! Take(adapter)
          right ! Take(adapter)
          hungry
      }
    }

  //When a hakker is hungry it tries to pick up its chopsticks and eat
  //When it picks one up, it goes into wait for the other
  //If the hakkers first attempt at grabbing a chopstick fails,
  //it starts to wait for the response of the other grab
  lazy val hungry: Behavior[HakkerMessage] =
    Behaviors.receiveMessagePartial {
      case ChopstickAnswerAdaptor(Taken(`left`)) =>
        waitForOtherChopstick(chopstickToWaitFor = right, takenChopstick = left)

      case ChopstickAnswerAdaptor(Taken(`right`)) =>
        waitForOtherChopstick(chopstickToWaitFor = left, takenChopstick = right)

      case ChopstickAnswerAdaptor(Busy(chopstick)) =>
        firstChopstickDenied
    }

  //When a hakker is waiting for the last chopstick it can either obtain it
  //and start eating, or the other chopstick was busy, and the hakker goes
  //back to think about how he should obtain his chopsticks :-)
  def waitForOtherChopstick(chopstickToWaitFor: ActorRef[ChopstickMessage],
                            takenChopstick: ActorRef[ChopstickMessage]): Behavior[HakkerMessage] =

    Behaviors.setup { ctx =>
      val adapter = ctx.messageAdapter(ChopstickAnswerAdaptor)
      Behaviors.receiveMessagePartial {
        case ChopstickAnswerAdaptor(Taken(`chopstickToWaitFor`)) =>
          println(s"$name has picked up ${left.path.name} and ${right.path.name} and starts to eat")
          startEating(ctx, 5.seconds)

        case ChopstickAnswerAdaptor(Busy(`chopstickToWaitFor`)) =>
          takenChopstick ! Put(adapter)
          startThinking(ctx, 10.milliseconds)
      }
    }

  //When a hakker is eating, he can decide to start to think,
  //then he puts down his chopsticks and starts to think
  lazy val eating: Behavior[HakkerMessage] =
    Behaviors.setup { ctx =>
      val adapter = ctx.messageAdapter(ChopstickAnswerAdaptor)
      Behaviors.receiveMessagePartial {
        case Think =>
          println(s"$name puts down his chopsticks and starts to think")
          left ! Put(adapter)
          right ! Put(adapter)
          startThinking(ctx, 5.seconds)
      }
    }

  //When the results of the other grab comes back,
  //he needs to put it back if he got the other one.
  //Then go back and think and try to grab the chopsticks again
  lazy val firstChopstickDenied: Behavior[HakkerMessage] =
    Behaviors.setup { ctx =>
      val adapter = ctx.messageAdapter(ChopstickAnswerAdaptor)
      Behaviors.receiveMessagePartial {
        case ChopstickAnswerAdaptor(Taken(chopstick)) =>
          chopstick ! Put(adapter)
          startThinking(ctx, 10.milliseconds)
        case ChopstickAnswerAdaptor(Busy(_)) =>
          startThinking(ctx, 10.milliseconds)
      }
    }

  private def startThinking(ctx: ActorContext[HakkerMessage], duration: FiniteDuration) = {
    ctx.schedule(duration, ctx.self, Eat)
    thinking
  }

  private def startEating(ctx: ActorContext[HakkerMessage], duration: FiniteDuration) = {
    ctx.schedule(duration, ctx.self, Think)
    eating
  }
}


/*
* Alright, here's our test-harness
*/
object DiningHakkersTyped {

  def mainBehavior: Behavior[NotUsed] = Behaviors.setup { context =>

    //Create 5 chopsticks
    val chopsticks = for (i <- 1 to 5) yield context.spawn(Chopstick.available, "Chopstick" + i)

    //Create 5 awesome hakkers and assign them their left and right chopstick
    val hakkers = for {
      (name, i) <- List("Ghosh", "Boner", "Klang", "Krasser", "Manie").zipWithIndex
    } yield context.spawn(new Hakker(name, chopsticks(i), chopsticks((i + 1) % 5)).waiting, name)

    //Signal all hakkers that they should start thinking, and watch the show
    hakkers.foreach(_ ! Think)

    Behaviors.empty
  }

  def main(args: Array[String]): Unit = {
    ActorSystem(mainBehavior, "DinningHakkers")
  }
}

