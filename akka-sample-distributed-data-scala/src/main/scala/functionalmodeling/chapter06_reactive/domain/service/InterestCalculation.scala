package functionalmodeling.chapter06_reactive
package domain
package service

import scalaz.Kleisli

trait InterestCalculation[Account, Amount] {
  def computeInterest: Kleisli[Valid, Account, Amount]
}
