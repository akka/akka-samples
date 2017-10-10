package akka_tut

import java.util.concurrent.{ExecutorService, Executors}

import akka.actor.{ActorLogging, ActorRef, ActorSystem, Props}
import akka.persistence.{PersistentActor, RecoveryCompleted, SaveSnapshotSuccess, SnapshotOffer}
import akka.pattern._
import akka.util.Timeout
import akka_tut.LotteryActor.{FailureEvent, Lottery, LotteryCmd, LuckyEvent}

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * https://scala.cool/2017/07/learning-akka-7/
  */
object LotteryActor {
  case class LotteryCmd(userId: Int, username: String, email: String)
  trait LotteryEvent
  case class LuckyEvent(userId: Int, luckyMoney: Int) extends LotteryEvent
  case class FailureEvent(userId: Int, reason: String) extends LotteryEvent
  case class Lottery(totalAmount: Int, remainAmount: Int, num: Int) {
    def update(luckMoney: Int) = copy(remainAmount = remainAmount - luckMoney, num = num - 1)  // need verify remainAmount before
  }
}

class LotteryActor(initState: Lottery) extends PersistentActor with ActorLogging {

  override def persistenceId: String = "lottery-actor-2"

  var state = initState

  override def receiveRecover: Receive = {
    case event: LuckyEvent =>
      updateState(event)      // 恢复actor时根据持久化的事件恢复actor状态

    case SnapshotOffer(_, snapshot: Lottery) =>
      log.info(s"Recover actor state from snapshot and the snapshot is ${snapshot}")
      state = snapshot

    case RecoveryCompleted =>
      log.info("the actor recover completed")
  }

  private def updateState(event: LotteryActor.LuckyEvent) = {
    state = state.update(event.luckyMoney)
  }

  override def receiveCommand: Receive = {
    case lc: LotteryCmd =>    // 进行抽奖，并得到抽奖结果，根据结果做出不同的处理
      doLottery(lc) match {     // 抽到随机红包
        case le: LuckyEvent =>
          persist(le) { event =>
            updateState(event)
            increaseEvtCountAndSnapshot()
            sender() ! event
          }

        case fe: FailureEvent =>
          sender() ! fe
      }

    case "saveSnapshot" =>
      saveSnapshot(state)

    case SaveSnapshotSuccess(metadata) =>
      log.info(s"snapshot $metadata is save completed")      
  }

  private def doLottery(lc: LotteryCmd) = {
    if (state.remainAmount > 0) {
      val luckMoney = scala.util.Random.nextInt(state.remainAmount) + 1
      LuckyEvent(lc.userId, luckMoney)
    } else {
      FailureEvent(lc.userId, "红包已被抢完， 下次趁早！")
    }
  }

  private def increaseEvtCountAndSnapshot() = {
    val snapshotInterval = 5
    if (lastSequenceNr % snapshotInterval == 0 && lastSequenceNr != 0) {
      self ! "saveSnapshot"
    }
  }
}

class LotteryRun(lotteryActor: ActorRef, cmd: LotteryCmd) extends Runnable {
  implicit val timeout = Timeout(3.seconds)

  override def run(): Unit = {
    for {
      fut <- lotteryActor ? cmd
    } yield fut match {
      case le: LuckyEvent => println(s"恭喜用户${le.userId}投到了${le.luckyMoney}元红包")
      case fe: FailureEvent => println(fe.reason)
      case _ => println("系统错误，请重新抽奖")
    }
  }

}
object LotteryApp {
  def main(args: Array[String]): Unit = {
    val lottery = Lottery(100, 100, 10)
    val system = ActorSystem("lottery-system")
    val lotteryActor = system.actorOf(Props(new LotteryActor(lottery)), "LotteryActor-1")
    val pool: ExecutorService = Executors.newFixedThreadPool(10)

    val runs = (1 to 20).map { i =>
      new LotteryRun(lotteryActor, LotteryCmd(i, "James", "james@gmail.com"))
    }

    runs.map(pool.execute(_))

    Thread.sleep(5000)
    pool.shutdown()
    system.terminate()

  }
}