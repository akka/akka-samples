package functionalmodeling.chapter05_modularization
package domain
package service

import scalaz.Kleisli

trait InterestCalculation[Account, Amount] {
  def computeInterest: Kleisli[Valid, Account, Amount]
}
