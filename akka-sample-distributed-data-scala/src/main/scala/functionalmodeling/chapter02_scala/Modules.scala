package functionalmodeling.chapter02_scala

/**
  * 2017－08－12
  */
object Modules {

  sealed trait TaxType
  case object Tax extends TaxType
  case object Fee extends TaxType
  case object Commission extends TaxType

  sealed trait TransactionType
  case object InterestComputation extends TransactionType
  case object Dividend extends TransactionType

  type Amount = BigDecimal

  case class Balance(amount: Amount = 0)

  trait TaxCalculationTable {
    type T <: TransactionType
    val transactionType: T

    def getTaxRate: Map[TaxType, Amount] = Map(
      Tax -> 0.02,
      Fee -> 0.05,
      Commission -> 0.01
    )

  }

  trait TaxCalculation {
    type S <: TaxCalculationTable

    val table: S

    def calculate(taxOn: Amount): Amount =
      table.getTaxRate.map { case (t, r) =>
        doCompute(taxOn, r)
      }.sum

    protected def doCompute(taxOn: Amount, rate: Amount): Amount = {
      taxOn * rate
    }
  }

  trait SingaporeTaxCalculation extends TaxCalculation {
    def calculateGST(tax: Amount, gsRate: Amount) = tax * gsRate
  }

  trait InterestCalculation {
    type C <: TaxCalculation
    val taxCalculation: C

    def interest(b: Balance): Option[Amount] = Some(b.amount * 0.05)

    def calculate(balance: Balance): Option[Amount] =
      interest(balance).map { i =>
        i - taxCalculation.calculate(i)
      }
  }

  object InterestTaxCalculationTable extends TaxCalculationTable {
    type T = TransactionType
    val transactionType = InterestComputation
  }

  object TaxCalculation extends TaxCalculation {
    type S = TaxCalculationTable
    val table = InterestTaxCalculationTable
  }

  object InterestCalculation extends InterestCalculation {
    type C = TaxCalculation
    val taxCalculation = TaxCalculation
  }
}
