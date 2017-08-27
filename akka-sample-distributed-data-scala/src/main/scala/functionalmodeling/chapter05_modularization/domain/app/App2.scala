package functionalmodeling.chapter05_modularization
package domain
package app

import scalaz._
import Scalaz._

import \/._

import repository.interpreter.AccountRepositoryInMemory
import model._
import common._
import Account._

/**
  * Created by liaoshifu on 2017/8/26
  */
object App2 {

  def main(args: Array[String]): Unit = {
    import AccountRepositoryInMemory._

    val account = checkingAccount("a-123", "james", today.some, None, Balance()).toOption.get

    val bb  = for {
      b <- updateBalance(account, 10000)
      c <- store(b)
      d <- balance(c.no)
    } yield d

    println(bb)
  }
}
