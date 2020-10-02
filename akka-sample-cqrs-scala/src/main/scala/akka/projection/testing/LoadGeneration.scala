package akka.projection.testing

import akka.{Done, NotUsed}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, Terminated}
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.pattern.StatusReply
import akka.projection.testing.LoadGeneration.{Failed, Result, RunTest}
import akka.projection.testing.LoadTest.Start
import akka.stream.scaladsl.Source
import akka.util.Timeout
import javax.sql.DataSource

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

object LoadGeneration {

  case class RunTest(name: String, actors: Int, eventsPerActor: Int, reply: ActorRef[Result])

  sealed trait Result

  case class Pass() extends Result

  case class Failed(t: Option[Throwable], expected: Int, got: Int) extends Result

  def apply(shardRegion: ActorRef[ShardingEnvelope[ConfigurablePersistentActor.Command]], source: DataSource): Behavior[RunTest] = Behaviors.setup { ctx =>
    Behaviors.receiveMessage[RunTest] {
      case rt@RunTest(name, actors, eventsPerActor, reply) =>
        ctx.spawn(LoadTest(name, shardRegion, source), s"test-$name") ! Start(rt)
        Behaviors.same
    }
  }

}

object LoadTest {

  sealed trait Command

  case class Start(test: RunTest) extends Command

  private case class StartValidation() extends Command

  private case class LoadGenerationFailed(t: Throwable) extends Command

  def apply(testName: String, shardRegion: ActorRef[ShardingEnvelope[ConfigurablePersistentActor.Command]], source: DataSource): Behavior[Command] = Behaviors.setup { ctx =>
    import akka.actor.typed.scaladsl.AskPattern._
    implicit val timeout: Timeout = 30.seconds
    implicit val system = ctx.system
    implicit val ec: ExecutionContext = system.executionContext
    Behaviors.receiveMessage[Command] {
      case Start(RunTest(_, actors, eventsPerActor, replyTo)) =>
        ctx.log.info("Starting load generation")
        val expected = actors * eventsPerActor
        val testRun: Source[StatusReply[Done], NotUsed] = Source(1 to actors)
          .flatMapConcat(id =>
            Source(1 to eventsPerActor)
              .mapAsync(1)(message => shardRegion.ask[StatusReply[Done]] { replyTo =>
                ShardingEnvelope(s"$id", ConfigurablePersistentActor.PersistAndAck(s"actor-$id-message-$message", replyTo, testName))
              }))
        ctx.pipeToSelf(testRun.run()) {
          case Success(_) => StartValidation()
          case Failure(t) => LoadGenerationFailed(t)
        }
        Behaviors.receiveMessage[Command] {
          case StartValidation() =>
            ctx.log.info("Starting validation")
            val validation = ctx.spawn(TestValidation(replyTo, testName, expected, source: DataSource), s"TestValidation=$testName")
            ctx.watch(validation)
            Behaviors.same
          case LoadGenerationFailed(t) =>
            ctx.log.error("Load generation failed", t)
            replyTo ! Failed(Some(t), -1, -1)
            Behaviors.stopped
        }.receiveSignal {
          case (ctx, Terminated(_)) =>
            ctx.log.info("Validation finished, terminating")
            Behaviors.stopped
        }
    }
  }

}
