package akka_tut.stream

import java.awt.Dimension
import java.awt.event.{MouseAdapter, MouseEvent}
import javax.swing.JFrame

import akka.actor.{ActorRef, ActorSystem}
import akka.stream._
import akka.stream.scaladsl.Source
import akka.stream.stage._

import scala.collection._
import scala.concurrent.duration._

/**
  * https://stackoverflow.com/questions/35120082/how-to-get-started-with-akka-streams
  */
object ClickStreamExample {

  implicit class SourceEnriched[A, Mat](stream: Source[A, Mat]) {
    /**
      * Accumulates elements as long as they arrive within the time of `duration`
      * after the previous element.
      */
    def throttle(duration: FiniteDuration): Source[immutable.Seq[A], Mat] = {
      require(duration > Duration.Zero)
      stream.via(new Throttle[A](duration)).withAttributes(Attributes.name("throttle"))
    }
  }

  def mkClickFrame(ref: ActorRef): Unit = {
    new JFrame("Click Stream Example") {
      setPreferredSize(new Dimension(300, 300))
      setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE)
      addMouseListener(new MouseAdapter {
        override def mouseClicked(e: MouseEvent): Unit = ref ! e
      })

      pack()
      setVisible(true)
    }
  }
  def main(args: Array[String]): Unit = {

    implicit val system = ActorSystem("TestSystem")
    implicit val materializer = ActorMaterializer()

    val clickStream = Source
      .actorRef[MouseEvent](bufferSize = 0, OverflowStrategy.fail)
      .mapMaterializedValue(mkClickFrame)

    val multiClickStream = clickStream
      .throttle(250.millis)
      .map(clickEvents => clickEvents.length)
      .filter(numberOfClicks => numberOfClicks >= 2)

    multiClickStream runForeach println
  }

  /**                                                ˜
    * Implementation copied from [[akka.stream.impl.fusing.GroupedWeightedWithin]] and
    * adapted to our needs
    */
  final class Throttle[A](duration: FiniteDuration) extends GraphStage[FlowShape[A, immutable.Seq[A]]] {
    val in = Inlet[A]("in")
    val out = Outlet[immutable.Seq[A]]("out")
    val shape = FlowShape(in, out)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new TimerGraphStageLogic(shape) {
      val buf: immutable.VectorBuilder[A] = new immutable.VectorBuilder[A]
      var groupClosed = false
      var finished = false

      val GroupedWithTimer = "GroupedWithinTimer"

      override def preStart(): Unit = {
        pull(in)
      }

      def nextElement(elem: A): Unit = {
        buf += elem
        scheduleOnce(GroupedWithTimer, duration)
        pull(in)
      }

      def closeGroup(): Unit = {
        groupClosed = true
        if (isAvailable(out)) emitGroup()
      }
      def emitGroup(): Unit = {
        push(out, buf.result())
        buf.clear()
        if (!finished) startNewGroup()
        else completeStage()
      }

      def startNewGroup(): Unit = {
        groupClosed = false
        if (isAvailable(in)) nextElement(grab(in))
        else if (!hasBeenPulled(in)) pull(in)
      }

      setHandler(in, new InHandler {
        override def onPush(): Unit = if (!groupClosed) {
          nextElement(grab(in))
        }

        override def onUpstreamFinish(): Unit = {
          finished = true
          if (!groupClosed) closeGroup()
          else completeStage()
        }

        override def onUpstreamFailure(ex: Throwable): Unit = failStage(ex)
      })

      setHandler(out, new OutHandler {
        override def onPull(): Unit = if (groupClosed) emitGroup()

        override def onDownstreamFinish(): Unit = completeStage()
      })

      override protected def onTimer(timerKey: Any): Unit = closeGroup()

    }
  }
}
