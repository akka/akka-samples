package functionalmodeling.chapter04_patterns

import scalaz._
import Scalaz._

/**
  * 4.4.1
  */
trait TradingNormal[Account, Market, Order, ClientOrder, Execution, Trade] {
  def clientOrders: ClientOrder => List[Order]
  //def execute: Market => Account => Order => List[Execution]
  def execute(m: Market, a: Account): Order => List[Execution]
  //def allocate: List[Account] => Execution => List[Trade]
  def allocate(as: List[Account]): Execution => List[Trade]
}

/*
trait Trading[Account, Market, Order, ClientOrder, Execution, Trade] {
  def clientOrders: Kleisli[List, ClientOrder, Order]
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
*/




