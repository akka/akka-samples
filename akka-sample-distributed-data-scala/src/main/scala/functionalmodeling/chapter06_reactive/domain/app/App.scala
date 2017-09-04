package functionalmodeling.chapter06_reactive
package domain
package app

import scalaz._
import Scalaz._
import Kleisli._
import scalaz.std.iterable._

import service.interpreter.{ AccountService, InterestPostingService, ReportingService }
import service._
import repository.interpreter.AccountRepositoryInMemory

import model.common._
import model.Account

import scala.concurrent._
import duration._
import ExecutionContext.Implicits.global

/**
  * https://github.com/debasishg/frdomain/blob/master/src/main/scala/frdomain/ch5/domain/app/app.scala
  */

object App {

  import AccountService._
  import InterestPostingService._
  import ReportingService._

  def postTransactions(a: Account, cr: Amount, db: Amount) = for {
    _ <- credit(a.no, cr)
    d <- debit(a.no, db)
  } yield d

  def composite(no: String, name: String, cr: Amount, db: Amount) = (for {
    a <- open(no, name, BigDecimal(0.4).some, None, Savings)
    t <- postTransactions(a, cr, db)
  } yield t) >=> computeInterest >=> computeTax            // >=> equals andThen
  
  def main(args: Array[String]): Unit = {

    val timeout = 2.seconds

    val x = composite("a-123", "lshoo", 20000, 200)(AccountRepositoryInMemory)

    println(Await.result(x.show, timeout))

    val opens = for {
      _ <- open("a1234", "a1name", None, None, Checking)
      _ <- open("a2345", "a2name", None, None, Checking)
      _ <- open("a3456", "a3name", BigDecimal(6.0).some, None, Savings)
      _ <- open("b1234", "b1name", None, None, Checking)
      _ <- open("b2345", "b2name", BigDecimal(5.0).some, None, Savings)
    } yield (())

    val credits = for {
      _ <- credit("a1234", 1000)
      _ <- credit("a2345", 2000)
      _ <- credit("a3456", 3000)
      _ <- credit("b1234", 2000)
    } yield (())

    val c = for {
      _ <- opens
      _ <- credits
      a <- balanceByAccount
    } yield a
    
    val y = c(AccountRepositoryInMemory)
    val yr = Await.result(y.show, timeout)
    println(yr)
    
    /*y.foreach { na =>
      na.foreach {
        case (no, amount) =>
          println(s"Account $no has the balance: $amount")
      }
    }*/
  }
}
