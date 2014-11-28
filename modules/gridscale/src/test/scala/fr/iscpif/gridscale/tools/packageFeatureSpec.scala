package fr.iscpif.gridscale.tools

import fr.iscpif.gridscale._
import fr.iscpif.gridscale.tools._
import scala.concurrent.duration._

import org.scalatest.junit._
import org.scalatest._

class packageFeatureSpec extends FeatureSpec with GivenWhenThen {

  feature("Durations can be converted to an HH:MM:SS format") {

    info("Durations are used to define walltimes")
    info("They need to be converted to strings formated as HH:MM:SS")
    info("So that they can be inserted in job definitions")

    scenario("Duration in seconds") {

      Given("A duration of 0 second")
      val t0 = 0 second
      val res0 = "00:00:00"
      When("Formatted as an HH:MM:SS string")
      val s0 = t0.toHHmmss
      Then(s"it should look like ${res0}")
      assert(res0 == s0)

      Given("A duration of 10 seconds")
      val t = 10 seconds
      val res = "00:00:10"
      When("Formatted as an HH:MM:SS string")
      val s = t.toHHmmss
      Then(s"it should look like ${res}")
      assert(res == s)

      Given("A duration of 70 seconds")
      val t1 = 70 seconds
      val res1 = "00:01:10"
      When("Formatted as an HH:MM:SS string")
      val s1 = t1.toHHmmss
      Then(s"it should look like ${res1}")
      assert(res1 == s1)

      Given("A duration of 4000 seconds")
      val t2 = 4000 seconds
      val res2 = "01:06:40"
      When("Formatted as an HH:MM:SS string")
      val s2 = t2.toHHmmss
      Then(s"it should look like ${res2}")
      assert(res2 == s2)
    }

    scenario("Duration in minutes") {

      Given("A duration of 0 minute")
      val t0 = 0 minute
      val res0 = "00:00:00"
      When("Formatted as an HH:MM:SS string")
      val s0 = t0.toHHmmss
      Then(s"it should look like ${res0}")
      assert(res0 == s0)

      Given("A duration of 10 minutes")
      val t = 10 minutes
      val res = "00:10:00"
      When("Formatted as an HH:MM:SS string")
      val s = t.toHHmmss
      Then(s"it should look like ${res}")
      assert(res == s)

      Given("A duration of 70 minutes")
      val t1 = 70 minutes
      val res1 = "01:10:00"
      When("Formatted as an HH:MM:SS string")
      val s1 = t1.toHHmmss
      Then(s"it should look like ${res1}")
      assert(res1 == s1)

      Given("A duration of 4000 minutes")
      val t2 = 4000 minutes
      val res2 = "66:40:00"
      When("Formatted as an HH:MM:SS string")
      val s2 = t2.toHHmmss
      Then(s"it should look like ${res2}")
      assert(res2 == s2)
    }

    scenario("Duration in hours") {

      Given("A duration of 0 hour")
      val t0 = 0 hour
      val res0 = "00:00:00"
      When("Formatted as an HH:MM:SS string")
      val s0 = t0.toHHmmss
      Then(s"it should look like ${res0}")
      assert(res0 == s0)

      Given("A duration of 10 hours")
      val t = 10 hours
      val res = "10:00:00"
      When("Formatted as an HH:MM:SS string")
      val s = t.toHHmmss
      Then(s"it should look like ${res}")
      assert(res == s)

      Given("A duration of 70 hours")
      val t1 = 70 hours
      val res1 = "70:00:00"
      When("Formatted as an HH:MM:SS string")
      val s1 = t1.toHHmmss
      Then(s"it should look like ${res1}")
      assert(res1 == s1)

      Given("A duration of 4000 hours")
      val t2 = 4000 hours
      val res2 = "4000:00:00"
      When("Formatted as an HH:MM:SS string")
      val s2 = t2.toHHmmss
      Then(s"it should look like ${res2}")
      assert(res2 == s2)
    }

    scenario("Duration in days") {

      Given("A duration of 0 day")
      val t0 = 0 day
      val res0 = "00:00:00"
      When("Formatted as an HH:MM:SS string")
      val s0 = t0.toHHmmss
      Then(s"it should look like ${res0}")
      assert(res0 == s0)

      Given("A duration of 10 days")
      val t = 10 days
      val res = "240:00:00"
      When("Formatted as an HH:MM:SS string")
      val s = t.toHHmmss
      Then(s"it should look like ${res}")
      assert(res == s)

      Given("A duration of 70 days")
      val t1 = 70 days
      val res1 = "1680:00:00"
      When("Formatted as an HH:MM:SS string")
      val s1 = t1.toHHmmss
      Then(s"it should look like ${res1}")
      assert(res1 == s1)

      Given("A duration of 4000 days")
      val t2 = 4000 days
      val res2 = "96000:00:00"
      When("Formatted as an HH:MM:SS string")
      val s2 = t2.toHHmmss
      Then(s"it should look like ${res2}")
      assert(res2 == s2)
    }

    scenario("Duration in hours, minutes, seconds") {

      Given("A duration of 1 hour and 15 seconds")
      val t0 = 1.hour + 15.seconds
      val res0 = "01:00:15"
      When("Formatted as an HH:MM:SS string")
      val s0 = t0.toHHmmss
      Then(s"it should look like ${res0}")
      assert(res0 == s0)

      Given("A duration of 52 minutes and 42 seconds")
      val t = 52.minutes + 42.seconds
      val res = "00:52:42"
      When("Formatted as an HH:MM:SS string")
      val s = t.toHHmmss
      Then(s"it should look like ${res}")
      assert(res == s)

      Given("A duration of 1 hour and 75 minutes")
      val t1 = 1.hour + 75.minutes
      val res1 = "02:15:00"
      When("Formatted as an HH:MM:SS string")
      val s1 = t1.toHHmmss
      Then(s"it should look like ${res1}")
      assert(res1 == s1)

      Given("A duration of 35 hours, 67 minutes and 142 seconds")
      val t2 = 35.hours + 67.minutes + 142.seconds
      val res2 = "36:09:22"
      When("Formatted as an HH:MM:SS string")
      val s2 = t2.toHHmmss
      Then(s"it should look like ${res2}")
      assert(res2 == s2)

      Given("A duration of 2 days 35 hours, 67 minutes and 142 seconds")
      val t3 = 2.days + 35.hours + 67.minutes + 142.seconds
      val res3 = "84:09:22"
      When("Formatted as an HH:MM:SS string")
      val s3 = t3.toHHmmss
      Then(s"it should look like ${res3}")
      assert(res3 == s3)
    }
  }
}
