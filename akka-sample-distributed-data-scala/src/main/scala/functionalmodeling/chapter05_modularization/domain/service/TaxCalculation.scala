package functionalmodeling.chapter05_modularization
package domain
package service

import scalaz.Kleisli

trait TaxCalculation[Amount] {
  def computeTax: Kleisli[Valid, Amount, Amount]
}
