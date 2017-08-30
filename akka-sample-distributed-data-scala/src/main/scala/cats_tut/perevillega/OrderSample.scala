package cats_tut.perevillega

import java.util.UUID

import cats.free.{Free, Inject}
import Free._
import cats.{Id, ~>}
import cats.instances.list._
import cats.syntax.traverse._
import cats.instances.either._
import cats.data.Coproduct

import scala.language.higherKinds

/**
  * http://perevillega.com/understanding-free-monads
  */
object OrderSample {

  type Symbol = String
  type Response = String
  type UserId = String
  type JobId = String
  type Action = String
  type Values = String
  type SourceId = String
  type MessageId = String
  type ChannelId = String
  type Condition = String
  type Payload = String

  /**
    * 1. Create ADT language
    * @tparam A
    */
  sealed trait Orders[A]
  case class Buy(stock: Symbol, amount: Int) extends Orders[Response]
  case class Sell(stock: Symbol, amount: Int) extends Orders[Response]
  case class ListStocks() extends Orders[List[Response]]

  sealed trait Log[A]
  case class Info(msg: String) extends Log[Unit]
  case class Error(msg: String) extends Log[Unit]

  sealed trait Audit[A]
  case class UserActionAudit(user: UserId, action: Action, values: List[Values]) extends Audit[Unit]
  case class SystemActionAudit(job: JobId, action: Action, values: List[Values]) extends Audit[Unit]

  sealed trait Messaging[A]
  case class Publish(channelId: ChannelId, source: SourceId, messageId: MessageId, payload: Payload) extends Messaging[Response]
  case class Subscribe(channelId: ChannelId, filterBy: Condition) extends Messaging[Payload]

  /**
    * 2. create free monad type using cats
    */
  type OrdersF[A] = Free[Orders, A]

  type LogF[A] = Free[Log, A]

  type MessagingF[A] = Free[Messaging, A]

  /**
    * 3. smart constructor lift to monad(boilerplate)
    */
  //def buy(stock: Symbol, amount: Int): OrdersF[Response] = liftF[Orders, Response](Buy(stock, amount))
  //def sell(stock: Symbol, amount: Int): OrdersF[Response] = liftF[Orders, Response](Sell(stock, amount))
  //def listStocks(): OrdersF[List[Response]] = liftF[Orders, List[Response]](ListStocks())

  //def info(msg: String): LogF[Unit] = liftF[Log, Unit](Info(msg))
  //def error(msg: String): LogF[Unit] = liftF[Log, Unit](Error(msg))
  class OrderI[F[_]](implicit I: Inject[Orders, F]) {
    def listStocksI(): Free[F, List[Response]] = inject[Orders, F](ListStocks())
    def buyI(stock: Symbol, amount: Int): Free[F, Response] = inject[Orders, F](Buy(stock, amount))
    def sellI(stock: Symbol, amount: Int):Free[F, Response] = inject[Orders, F](Sell(stock, amount))
  }

  object OrderI {
    implicit def orderI[F[_]](implicit I: Inject[Orders, F]): OrderI[F] = new OrderI[F]
  }

  class LogI[F[_]](implicit I: Inject[Log, F]) {
    def infoI(msg: String): Free[F, Unit] = inject[Log, F](Info(msg))
    def errorI(msg: String): Free[F, Unit] = inject[Log, F](Error(msg))
  }

  object LogI {
    implicit def logI[F[_]](implicit I: Inject[Log, F]): LogI[F] = new LogI[F]
  }

  class AuditI[F[_]](implicit I: Inject[Audit, F]) {
    def userAction(user: UserId, action: Action, values: List[Values]): Free[F, Unit] = inject[Audit, F](UserActionAudit(user, action, values))
    def systemAction(job:JobId, action: Action, values: List[Values]): Free[F, Unit] = inject[Audit, F](SystemActionAudit(job, action, values))
  }

  object AuditI {
    implicit def auditI[F[_]](implicit I: Inject[Audit, F]): AuditI[F] = new AuditI[F]()
  }

  class MessagingI[F[_]](implicit I: Inject[Messaging, F]) {
    def publish(channelId: ChannelId, source: SourceId, messageId: MessageId, payload: Payload): Free[F, Response] =
      inject[Messaging, F](Publish(channelId, source, messageId, payload))
    def subscribe(channelId: ChannelId, filterBy: Condition): Free[F, Payload] =
      inject[Messaging, F](Subscribe(channelId, filterBy))
  }

  object MessagingI {
    implicit def messageI[F[_]](implicit I: Inject[Messaging, F]) = new MessagingI[F]
  }

  /*def publish(channelId: ChannelId, source: SourceId, messageId: MessageId, payload: Payload): MessagingF[Response] =
    liftF[Messaging, Response](Publish(channelId, source, messageId, payload))

  def subscribe(channelId: ChannelId, filterBy: Condition): MessagingF[Payload] =
    liftF[Messaging, Payload](Subscribe(channelId, filterBy))
*/
  /**
    * 4. program action
    */
  //val smartTrade: OrdersF[Response] = for {
  def smartTrade(implicit I: OrderI[Orders]) = {
    import I._
    for {
      _ <- buyI("APPL", 50)
      _ <- buyI("MSFT", 10)
      rsp <- sellI("GOOG", 200)
    } yield rsp
  }

  //val smartTradeWithList: OrdersF[List[Response]] = for {
  def smartTradeWithList(implicit O: OrderI[Orders]) = {
    import O._
    for {
      st <- listStocksI()
      _ <- st.traverseU(buyI(_, 100))
      _ <- sellI("GOOG", 100)
    } yield st
  }

  type TradeApp[A] = Coproduct[Orders, Log, A]

  def smartTradeWithLogs(implicit O: OrderI[TradeApp], L: LogI[TradeApp]) = {
    import O._, L._
    for {
      _ <- infoI("I'm going to trade smartly")
      _ <- buyI("APPL", 100)
      _ <- infoI("I'm going to trade even more smartly")
      st <- listStocksI()
      _ <- st.traverseU(buyI(_, 200))
      rsp <- sellI("GOOG", 100)
      _ <- errorI("Wait, what?")
    } yield rsp
  }

  type AuditableTradeApp[A] = Coproduct[Audit, TradeApp, A]

  def smartTradeWithAuditsAndLogs(implicit O: OrderI[AuditableTradeApp],
                                  L: LogI[AuditableTradeApp],
                                  A: AuditI[AuditableTradeApp]) = {
    import O._, L._, A._

    for {
      _ <- infoI("I'm going to trade smartly")
      _ <- userAction("ID102", "buy", List("APPL"))
      _ <- buyI("APPL", 200)
      _ <- infoI("I'm going to trade even more smartly")
      _ <- userAction("ID102", "buy", List("MSFT", "100"))
      _ <- buyI("MSFT", 100)
      _ <- userAction("ID102", "sell", List("GOOG", "100"))
      rsp <- sellI("GOOG", 300)
      _ <- systemAction("BACKOFFICE", "tradesCheck", List("ID102", "lastTrades"))
      _ <- errorI("Wait, what?!")
    } yield rsp
  }

  def messagingPayload(implicit M: MessagingI[Messaging]) = {

    for {
      _ <- M.publish("BBC", "Sherlock", UUID.randomUUID().toString, "Run Moriarty")
      _ <- M.publish("BBC", "Adler", UUID.randomUUID().toString, "Sherlocked")
      payload <- M.subscribe("BBC", "Sherlock")
    } yield payload
  }

  /*val testMessagingInterpreter: MessagingF[Payload] = for {
    _ <- publish("BBC", "Sherlock", UUID.randomUUID().toString, "Run Moriarty")
    _ <- publish("BBC", "Adler", UUID.randomUUID().toString, "Sherlocked")
    payload <- subscribe("BBC", "Sherlock")
  } yield payload
  */
  /**
    * 5. interpreter
    */
  def orderPrinter: Orders ~> Id = new (Orders ~> Id) {
    override def apply[A](fa: Orders[A]): Id[A] = fa match {
      case ListStocks() =>
        println(s"Getting list of stocks: FB, TWTR")
        List("FB", "TWTR")

      case Buy(s, a) =>
        println(s"Buying $a of $s")
        s"Buy($a of $s) ok"

      case Sell(s, a) =>
        println(s"Selling $a of $s")
        s"Sell($a of $s) ok"
    }
  }
  
  type ErrorOr[A] = Either[String, A]

  def xorInterpreter: Orders ~> ErrorOr = new (Orders ~> ErrorOr) {
    override def apply[A](fa: Orders[A]): ErrorOr[A] = fa match {
      case ListStocks() =>
        Right(List("FB", "TWTR"))
      case Buy(s, a) =>
        Right(s"$s - $a")
      case Sell(s, a) =>
        Left(s"$s - $a")
    }
  }

  def logPrinter: Log ~> Id = new (Log ~> Id) {
    override def apply[A](fa: Log[A]) = fa match {
      case Info(msg) =>
        println(s"[Info] - $msg")

      case Error(msg) =>
        println(s"[Error] - $msg")
    }
  }

  def composedInterpreter: TradeApp ~> Id = orderPrinter or logPrinter

  def auditPrinter: Audit ~> Id = new (Audit ~> Id) {
    override def apply[A](fa: Audit[A]) = fa match {
      case UserActionAudit(user, action, values) =>
        println(s"[USER Action] - user $user called $action with value $values")

      case SystemActionAudit(job, action, values) =>
        println(s"[SYSTEM Action] - job $job called $action with values $values")
    }
  }

  def auditableInterpreter: AuditableTradeApp ~> Id = auditPrinter or composedInterpreter

  def messagingPrinter: Messaging ~> Id = new (Messaging ~> Id) {
    override def apply[A](fa: Messaging[A]) = fa match {
      case Publish(channelId, source, messageId, payload) =>
        val body = s"Publish [$channelId] From: [$source] Id: [$messageId] Payload: [$payload]"
        println(body)
        s"$body published ok"

      case Subscribe(channelId, filterBy) =>
        val payload = "Event fired"
        val body = s"Received message from [$channelId] (filter: [$filterBy]): $payload"
        println(body)
        s"$body received ok"
    }
  }

  def orderToMessageInterpreter(implicit I: MessagingI[Messaging]): Orders ~> MessagingF = new (Orders ~> MessagingF) {
    import I._
    override def apply[A](fa: Orders[A]): MessagingF[A] = fa match {
      case ListStocks() =>
        for {
          _ <- publish("001", "Orders", UUID.randomUUID().toString, "Get Stocks List")
          payload <- subscribe("001", "*")
        } yield List(payload)

      case Buy(stock, amount) =>
        publish("001", "Orders", UUID.randomUUID().toString, s"Buy $stock $amount")

      case Sell(stock, amount) =>
        publish("001", "Orders", UUID.randomUUID().toString, s"Sell $stock $amount")
    }
  }


  def messagingFreePrinter: MessagingF ~> Id = new (MessagingF ~> Id) {
    override def apply[A](fa: MessagingF[A]) = fa.foldMap(messagingPrinter)
  }

  def ordersToTerminalViaMessage: Orders ~> Id = orderToMessageInterpreter andThen messagingFreePrinter

  def composedViaMessageInterpreter: TradeApp ~> Id = ordersToTerminalViaMessage or logPrinter

  def auditableToTerminalViaMessage: AuditableTradeApp ~> Id = auditPrinter or composedViaMessageInterpreter

  /**
    * 6. execute
    * @param args
    */
  def main(args: Array[String]): Unit = {
    import OrderI._, LogI._, MessagingI._

    println(s"> Smart trade - see what program is compiled onto - $smartTrade")
    println()

    val ro = smartTrade.foldMap(orderPrinter)
    println(s">The Smart trade - printer - is $ro")

    val rox = smartTrade.foldMap(xorInterpreter)
    println(s">The Smart trade - xorInterpreter - is $rox")

    val rol = smartTradeWithList.foldMap(orderPrinter)
    println(s">The Smart trade - smartTradeWithList - is $rol")

    val rolm = smartTradeWithLogs.foldMap(composedInterpreter)
    println(s">The Smart trade - smartTradeWithLogs - is $rol")

    val raolm = smartTradeWithAuditsAndLogs.foldMap(auditableInterpreter)
    println(s">The Smart trade - smartTradeWithAuditsAndLogs - $raolm")
    println()

    val rt = messagingPayload.foldMap(messagingPrinter)

    println(s"> Messaging layer - test messaging layer - $rt")
    println()

    val rtt = smartTrade.foldMap(orderToMessageInterpreter)
    println(s"> Smart trade - messaging Interpreter - ${rtt}")
    println()

    println(s"> Smart trade - smartTradeWithAuditsAndLogs + messaging Interpreter + printer - ${smartTradeWithAuditsAndLogs.foldMap(auditableToTerminalViaMessage)}")
    println()
  }
}
