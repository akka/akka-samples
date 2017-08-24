package functionalmodeling.chapter04_patterns
package trading

import scalaz.{ Order => Orderz, _ }
import Scalaz._
import Kleisli._

import TradeModel._

trait TradingInterpreter extends Trading[Account, Trade, ClientOrder, Order, Execution, Market] {

  override def clientOrders: Kleisli[List, List[ClientOrder], Order] = kleisli(fromClientOrders)

  override def execute(market: Market, brokerAccount: Account) = kleisli[List, Order, Execution] { order =>
    order.items.map { item =>
      Execution(brokerAccount, item.ins, "e-123", market, item.price, item.qty)
    }
  }

  override def allocate(as: List[Account]) = kleisli[List, Execution, Trade] { execution =>
    val q = execution.quantity / as.size
    as.map { a =>
      makeTrade(a, execution.instrument, "t-123", execution.market, execution.unitPrice, q)
    }
  }
}

object TradingInterpreter extends TradingInterpreter