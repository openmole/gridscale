package gridscale.tools

import org.scalatest._
import squants.information._

import scala.language.postfixOps

class InformationDecoratorSpec extends FeatureSpec with GivenWhenThen {

  private def scenarioEntry(inMemSize: Double, baseUnit: InformationUnit, expectedSize: Long) = {
    Given(s"A size of $inMemSize ${baseUnit.symbol}")
    val t0 = baseUnit(inMemSize)
    val res0 = s"$expectedSize"
    When("Formatted as a megabytes string")
    val s0 = t0.toMBString
    Then(s"it should appear as $res0 ${Megabytes.symbol}")
    assert(res0 == s0)
  }

  feature("Informtion can be converted to a megabytes string") {

    info("Information are used to define memory requirements")
    info("They need to be converted to strings containing an integer value")
    info("So that they can be inserted in job definitions")

    val testValues = Seq(0, 10, 4000, 1000000, 20000000, 12345678, 29654389, 43000267.19, -10, 55554555.55)

    scenario("Information in bytes") {

      val expectedResults = Seq(0, 0, 0, 1, 20, 12, 30, 43, 0, 56)
      for ((mem, expected) ← testValues.zip(expectedResults)) scenarioEntry(mem, Bytes, expected)

    }

    scenario("Information in kilobytes") {

      val expectedResults = Seq(0, 0, 4, 1000, 20000, 12346, 29654, 43000, 0, 55555)
      for ((mem, expected) ← testValues.zip(expectedResults)) scenarioEntry(mem, Kilobytes, expected)

    }

    scenario("Information in megabytes") {

      val expectedResults = Seq(0, 10, 4000, 1000000, 20000000, 12345678, 29654389, 43000267, 0, 55554556)
      for ((mem, expected) ← testValues.zip(expectedResults)) scenarioEntry(mem, Megabytes, expected)

    }

    scenario("Information in gigabytes") {

      val expectedResults = Seq(0, 10000, 4000000, 1000000000, 20000000000L, 12345678000L, 29654389000L, 43000267190L, 0, 55554555550L)
      for ((mem, expected) ← testValues.zip(expectedResults)) scenarioEntry(mem, Gigabytes, expected)

    }
  }
}
