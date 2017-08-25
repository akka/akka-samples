package functionalmodeling.chapter05_modularization
package domain
package service


trait InterestPostingService[Account, Amount] extends InterestCalculation[Account, Amount]
  with TaxCalculation[Amount]
