package payments.model

  sealed trait Correlated {
    def idempotentIdentifier: String
  }

  sealed trait Command extends Correlated {
    def amount: BigDecimal
  }

  case class AuthorizePayment(idempotentIdentifier:String, amount:BigDecimal)  extends Command 
  case class SettlePayment(idempotentIdentifier:String, amount: BigDecimal)    extends Command
  case class RefundPayment(idempotentIdentifier:String, amount:BigDecimal)     extends Command
  case class ChargebackPayment(idempotentIdentifier:String, amount:BigDecimal) extends Command

  sealed trait Event extends Correlated

  case class State(lifecycle:List[Event])

  sealed trait Balance extends Correlated {
    def amount: BigDecimal
  }

  case class Authorization(idempotentIdentifier: String, amount: BigDecimal) extends Balance
  case class Settlement(idempotentIdentifier: String, amount: BigDecimal)    extends Balance
  case class Refund(idempotentIdentifier: String, amount: BigDecimal)        extends Balance
  case class Chargeback(idempotentIdentifier: String, amount: BigDecimal)    extends Balance




 case class AuthorizationSuccessful(idempotentIdentifier: String, authorization: Authorization, successful: Boolean = true) extends Event
 case class AuthorizationFailed(idempotentIdentifier: String, message: String, successful: Boolean = false) extends Event
 case class AuthorizationTimeout(idempotentIdentifier: String, message: String, successful: Boolean = false) extends Event

 case class SettlementSuccessful(idempotentIdentifier: String, settlement: Settlement, successful: Boolean = true)  extends Event
 case class SettlementFailed(idempotentIdentifier: String, message: String, successful: Boolean = false) extends Event
 case class SettlementTimeout(idempotentIdentifier: String, message: String, successful: Boolean = false) extends Event

 case class RefundSuccessful(idempotentIdentifier: String, refund: Refund, successful: Boolean = true)  extends Event
 case class RefundFailed(idempotentIdentifier: String, message: String, successful: Boolean = false) extends Event
 case class RefundTimeout(idempotentIdentifier: String, message: String, successful: Boolean = false) extends Event
