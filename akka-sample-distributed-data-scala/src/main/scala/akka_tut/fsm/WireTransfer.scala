package akka_tut.fsm

import akka.actor.{Actor, ActorRef, FSM}
import akka.event.LoggingReceive
import akka_tut.fsm.WireTransfer.{TransferData, TransferState}

/**
  *
  */
object WireTransfer {
  case class Transfer(from: ActorRef, to: ActorRef, amount: BigInt)
  case object Done
  case object Failed

  sealed trait TransferState
  case object Initial extends TransferState
  case object AwaitFrom extends TransferState
  case object AwaitTo extends TransferState
  case object TransferDone extends TransferState

  sealed trait TransferData
  case object UninitializedWireTransferData extends TransferData
  case class InitialisedWireTransferData(from: ActorRef, to: ActorRef, amount: BigInt, client: ActorRef) extends TransferData
}

class WireTransfer extends AtMostOnceFSM[TransferState, TransferData] {
  import WireTransfer._

  startWith(Initial, UninitializedWireTransferData)

  when(Initial) (atMostOnce {
    case Event(Transfer(from, to, amount), UninitializedWireTransferData) =>
      from ! BankAccount.Withdraw(amount)
      goto(AwaitFrom) using InitialisedWireTransferData(from, to, amount, sender())
  })

  when(AwaitFrom) (atMostOnce {
    case Event(BankAccount.Done, InitialisedWireTransferData(_, to, amount, _)) =>
      to ! BankAccount.Deposit(amount)
      goto(AwaitTo)

    case Event(BankAccount.Failed, InitialisedWireTransferData(_, _, _, client)) =>
      client ! Failed
      goto(TransferDone)
      stop()
  })

  when(AwaitTo) (atMostOnce {
    case Event(BankAccount.Done, InitialisedWireTransferData(_, _, _, client)) =>
      client ! Done
      goto(TransferDone)
      stop()

    case Event(BankAccount.Failed, InitialisedWireTransferData(_, _ ,_ , client)) =>
      client ! Failed
      goto(TransferDone)
      stop()
  } )

  initialize()
  
}
