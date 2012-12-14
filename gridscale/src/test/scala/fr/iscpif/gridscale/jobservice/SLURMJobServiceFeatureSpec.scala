import org.scalatest.FeatureSpec
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.ShouldMatchers._

import fr.iscpif.gridscale.jobservice._
import fr.iscpif.gridscale.authentication.SSHPrivateKeyAuthentication
import java.io.File

import fr.iscpif.gridscale.jobservice.SSHJobService // test

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class SLURMJobServiceFeatureSpec extends FeatureSpec with GivenWhenThen {

  feature("The user can submit jobs to a SLURM-enabled cluster") {

    info("As a user")
    info("I want to be able to submit jobs to a SLURM-enabled cluster")
    info("So that I can get my simulations compute faster")

    scenario("a job is successfully submitted trough ssh") {

      given("a machine using an SSH privatekey authentication")
      implicit val sshJS = new SSHJobService with SSHPrivateKeyAuthentication {
        def host = "Master"
        def user = "jopasserat"
        def password = null
        def privateKey = new File("/Users/jopasserat/.ssh/id_dsa")
      }

      given("a simple job")
      val jobDesc = new SSHJobDescription {
        def executable = "/bin/echo"
        def arguments = "success > test_success.txt"
        def workDirectory = "/tmp"
      }

      when("when the job has been submitted")
      val j = sshJS.submit(jobDesc)
      then("it should complete one day or another")
      val s = untilFinished { Thread.sleep(5000); val s = sshJS.state(j); println(s); s }
      assert("Done" == s)

      //sshJS.purge(j)
    }

    scenario("a job is successfully submitted, runs and its results are retrived normally") {

      given("a slurm environment using an SSH privatekey authentication")
      implicit val slurmService = new SLURMJobService with SSHPrivateKeyAuthentication {
        def host = "Master"
        def user = "jopasserat"
        def password = null
        def privateKey = new File("/Users/jopasserat/.ssh/id_dsa")
      }

      given("a simple job")
      val description = new SLURMJobDescription {
        def executable = "/bin/echo"
        def arguments = "success > test_success.txt"
        def workDirectory = "/tmp"
      }

      when("when the job has been submitted")
      val j = slurmService.submit(description)
      println(description.toSLURM)

      then("it can be monitored")
      val s1 = slurmService.state(j)
      assert("Running" == s1 || "Submitted" == s1)
      println("Job is " + s1)

      and("it should complete one day or another")
      val s2 = untilFinished { Thread.sleep(5000); val s = slurmService.state(j); println(s); s }
      assert("Done" === s2)
      //      slurmService.purge(j)
    }

    scenario("a job is successfully submitted, then cancelled") {

      given("a slurm environment using an SSH privatekey authentication")
      implicit val slurmService = new SLURMJobService with SSHPrivateKeyAuthentication {
        def host = "Master"
        def user = "jopasserat"
        def password = null
        def privateKey = new File("/Users/jopasserat/.ssh/id_dsa")
      }

      given("an infinite job")
      val description = new SLURMJobDescription {
        def executable = "/bin/read"
        def arguments = ""
        def workDirectory = "/tmp"
      }

      when("when the job has been submitted")
      val j = slurmService.submit(description)
      println(description.toSLURM)

      then("it can be cancelled")
      val s1 = slurmService.cancel(j)

      and("it should appear as done")
      val s2 = untilFinished { Thread.sleep(5000); val s = slurmService.state(j); println(s); s }
      assert("Done" === s2)
      //slurmService.purge(j)
    }
  }
}
