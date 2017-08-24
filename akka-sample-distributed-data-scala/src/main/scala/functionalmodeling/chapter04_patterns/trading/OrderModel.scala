package functionalmodeling.chapter04_patterns
package trading

import java.util.{Calendar, Date}

trait OrderModel {
  this: RefModel =>

  case class LineItem(ins: Instrument, qty: BigDecimal, price: BigDecimal)
  case class Order(no: String, date: Date, customer: Customer, items: List[LineItem])

  type ClientOrder = Map[String, String]

  def fromClientOrders: List[ClientOrder] => List[Order] = { cos =>
    cos map { co =>
      val ins = co("instrument").split("-")
      val lineItems = ins map { in =>
        val arr = in.split("/")
        LineItem(arr(0), BigDecimal(arr(1)), BigDecimal(arr(2)))
      }

      Order(co("no"), Calendar.getInstance.getTime, co("customer"), lineItems.toList)
    }
  }
}
