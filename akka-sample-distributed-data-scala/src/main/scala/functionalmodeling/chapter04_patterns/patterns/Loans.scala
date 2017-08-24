package functionalmodeling.chapter04_patterns.patterns

import java.util.{Calendar, Date}

import scalaz._
import Kleisli._
import Scalaz._

/**
  * TODO
  */
object Loans {

  val today = Calendar.getInstance.getTime

  trait Applied
  trait Approved
  trait Enriched
  trait Disbursed

  case class LoanApplication[Status] private[Loans](
                                             date: Date,
                                             name: String,
                                             purpose: String,
                                             repayIn: Int,
                                             actualRepaymentYears: Option[Int] = None,
                                             startDate: Option[Date] = None,
                                             loanNo: Option[String] = None,
                                             emi: Option[BigDecimal] = None
                                           )

  type LoanApplied = LoanApplication[Applied]
  type LoanApproved = LoanApplication[Approved]
  type LoanEnriched = LoanApplication[Enriched]
  type LoadDisbursed = LoanApplication[Disbursed]

  def applyLoan(name: String, purpose: String, repayIn: Int, date: Date) =
    LoanApplication[Applied](date, name, purpose,repayIn)

  def approve = Kleisli[Option, LoanApplied, LoanApproved] { la =>
    la.copy(
      startDate = Calendar.getInstance().getTime.some,
      actualRepaymentYears = 15.some,
      loanNo = scala.util.Random.nextString(10).some
    ).some.map(identity[LoanApproved])
  }

  def enrich = Kleisli[Option, LoanApproved, LoanEnriched] { la =>

    val x = for {
      y <- la.actualRepaymentYears
      s <- la.startDate
    } yield (y, s)

    la.copy(emi = x.map { case (y, s) => calculateEMI(y, s)}).some.map(identity[LoanEnriched])
  }

  private def calculateEMI(tenure: Int, startDate: Date): BigDecimal = BigDecimal(0)

  def main(args: Array[String]): Unit = {
    val l = applyLoan("John B Rich", "House Building", 10, today)

    val op = approve andThen enrich

    val r = op run l
    println(r)

    //val nop = enrich andThen approve
  }
}
