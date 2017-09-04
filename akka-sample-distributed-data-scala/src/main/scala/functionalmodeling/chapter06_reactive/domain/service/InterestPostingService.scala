package functionalmodeling.chapter06_reactive.domain.service

trait InterestPostingService[Account, Amount] extends InterestCalculation[Account, Amount]
  with TaxCalculation[Amount]
