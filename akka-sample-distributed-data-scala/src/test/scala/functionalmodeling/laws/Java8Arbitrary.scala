package functionalmodeling.laws

import java.time._

import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.{Arbitrary, Gen}

import scala.collection.JavaConverters._

trait Java8Arbitrary {

  private [this] val minInstant: Instant = Instant.EPOCH
  private [this] val maxInstant: Instant = Instant.parse("3000-01-01T00:00:00.00Z")

  implicit val arbitraryZoneId: Arbitrary[ZoneId] = Arbitrary(
    Gen.oneOf(ZoneId.getAvailableZoneIds.asScala.map(ZoneId.of).toSeq)
  )

  implicit val arbitraryInstant: Arbitrary[Instant] = Arbitrary {
    Gen.choose(minInstant.getEpochSecond, maxInstant.getEpochSecond).map(Instant.ofEpochSecond)
  }

  implicit val arbitraryPeriod: Arbitrary[Period] = Arbitrary {
    for {
      years <- arbitrary[Int]
      months <- arbitrary[Int]
      days <- arbitrary[Int]
    } yield Period.of(years, months, days)
  }

  implicit val arbitraryLocalDateTime: Arbitrary[LocalDateTime] = Arbitrary (
    for {
      instant <- arbitrary[Instant]
      zoneId <- arbitrary[ZoneId]
    } yield LocalDateTime.ofInstant(instant, zoneId)
  )

  implicit val arbitraryZoneDateTime: Arbitrary[ZonedDateTime] = Arbitrary(
    for {
      instant <- arbitrary[Instant]
      zoneId <- arbitrary[ZoneId].suchThat(_ != ZoneId.of("GMT0"))    // #280 - avoid JDK-8138664
    } yield ZonedDateTime.ofInstant(instant, zoneId)
  )

  implicit val arbitraryOffsetDateTime: Arbitrary[OffsetDateTime] = Arbitrary(
    for {
      instant <- arbitrary[Instant]
      zoneId  <- arbitrary[ZoneId]
    } yield OffsetDateTime.ofInstant(instant, zoneId)
  )

  implicit val arbitraryLocalDate: Arbitrary[LocalDate] = Arbitrary(arbitrary[LocalDateTime].map(_.toLocalDate))

  implicit val arbitraryLocalTime: Arbitrary[LocalTime] = Arbitrary(arbitrary[LocalDateTime].map(_.toLocalTime))

  implicit val arbitraryYearMonth: Arbitrary[YearMonth] = Arbitrary(arbitrary[LocalDateTime].map(
    ldt => YearMonth.of(ldt.getYear, ldt.getMonth)
  ))

  implicit val arbitraryDuration: Arbitrary[Duration] = Arbitrary(
    for {
      first <- arbitrary[Instant]
      second <- arbitrary[Instant]
    } yield Duration.between(first, second)
  )
}
