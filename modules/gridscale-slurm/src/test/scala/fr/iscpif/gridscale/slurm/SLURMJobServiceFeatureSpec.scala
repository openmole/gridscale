package fr.iscpif.gridscale.slurm

import fr.iscpif.gridscale.ssh.{ SSHJobService, SSHPrivateKeyAuthentication }

import fr.iscpif.gridscale._
import java.io.File
import org.scalatest.junit._
import org.scalatest._

// test

class SLURMJobServiceFeatureSpec extends FeatureSpec with GivenWhenThen {

  // TODO won't run anymore since SSH lib was changed (I think)
  feature("The user can submit jobs to a SLURM-enabled cluster") {

    info("As a user")
    info("I want to be able to submit jobs to a SLURM-enabled cluster")
    info("So that I can get my simulations compute faster")

    scenario("a job is successfully submitted trough SLURM") {

      Given("a machine using an SSH privatekey authentication")
      implicit val slurmService = new SLURMJobService with SSHPrivateKeyAuthentication {
        def host = "Master"
        def user = "jopasserat"
        def password = ""
        def privateKey = new File("/Users/jopasserat/.ssh/id_dsa")
      }

      Given("a simple job")
      val jobDesc = new SLURMJobDescription {
        def executable = "/bin/echo"
        def arguments = "success > test_success.txt"
        def workDirectory = "~/toto"
      }

      When("When the job has been submitted")
      val j = slurmService.submit(jobDesc)
      Then("it should complete one day or another")
      val s = untilFinished { Thread.sleep(5000); val s = slurmService.state(j); println(s); s }
      assert("Done" == s)

      //sshJS.purge(j)
    }

    scenario("a job is successfully submitted, runs And its results are retrived normally") {

      Given("a slurm environment using an SSH privatekey authentication")
      implicit val slurmService = new SLURMJobService with SSHPrivateKeyAuthentication {
        def host = "Master"
        def user = "jopasserat"
        def password = ""
        def privateKey = new File("/Users/jopasserat/.ssh/id_dsa")
      }

      Given("a simple job")
      val description = new SLURMJobDescription {
        def executable = "/bin/echo"
        def arguments = "success > test_success.txt"
        def workDirectory = "~/toto"
      }

      When("When the job has been submitted")
      val j = slurmService.submit(description)
      println(description.toSLURM)

      Then("it can be monitored")
      val s1 = slurmService.state(j)
      assert("Running" == s1 || "Submitted" == s1)
      println("Job is " + s1)

      And("it should complete one day or another")
      val s2 = untilFinished { Thread.sleep(5000); val s = slurmService.state(j); println(s); s }
      assert("Done" === s2)
      //      slurmService.purge(j)
    }

    scenario("a job is successfully submitted, Then cancelled") {

      Given("a slurm environment using an SSH privatekey authentication")
      implicit val slurmService = new SLURMJobService with SSHPrivateKeyAuthentication {
        def host = "Master"
        def user = "jopasserat"
        def password = ""
        def privateKey = new File("/Users/jopasserat/.ssh/id_dsa")
      }

      Given("an infinite job")
      val description = new SLURMJobDescription {
        def executable = "/bin/read"
        def arguments = ""
        def workDirectory = "~/toto"
      }

      When("When the job has been submitted")
      val j = slurmService.submit(description)
      println(description.toSLURM)

      Then("it can be cancelled")
      val s1 = slurmService.cancel(j)

      And("it should appear as done")
      val s2 = untilFinished { Thread.sleep(5000); val s = slurmService.state(j); println(s); s }
      assert("Failed" === s2)
      //slurmService.purge(j)
    }

    scenario("a job request a gres gpu") {

      Given("a slurm environment using an SSH privatekey authentication")
      implicit val slurmService = new SLURMJobService with SSHPrivateKeyAuthentication {
        def host = "Master"
        def user = "jopasserat"
        def password = ""
        def privateKey = new File("/Users/jopasserat/.ssh/id_dsa")
      }

      Given("a CUDA job")
      val description = new SLURMJobDescription {
        def executable = "/opt/cuda/C/bin/linux/release/matrixMul"
        def arguments = ""
        def workDirectory = "~/toto"
        override def gres = List(new Gres("gpu", 1))
      }

      When("When the job has been submitted")
      val j = slurmService.submit(description)
      println(description.toSLURM)

      Then("it should be allocated a gres")
      val s1 = slurmService.state(j)

      And("it should appear as done after completion")
      val s2 = untilFinished { Thread.sleep(5000); val s = slurmService.state(j); println(s); s }
      assert("Done" === s2)
      //slurmService.purge(j)
    }
  }
}
