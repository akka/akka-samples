package functionalmodeling.chapter04_patterns
package trading

import scalaz._
import Scalaz._

trait Trading[Account, Trade, ClientOrder, Order, Execution, Market] {
  def clientOrders: Kleisli[List, List[ClientOrder], Order]
  def execute(m: Market, a: Account): Kleisli[List, Order, Execution]
  def allocate(as: List[Account]): Kleisli[List, Execution, Trade]

  def tradeGeneration(
                       market: Market,
                       broker: Account,
                       clientAccounts: List[Account]
                     ) = {
    clientOrders                andThen
      execute(market, broker)   andThen
      allocate(clientAccounts)
  }
}
