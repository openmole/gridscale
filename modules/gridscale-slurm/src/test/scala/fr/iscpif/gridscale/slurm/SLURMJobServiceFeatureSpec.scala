package fr.iscpif.gridscale.slurm

import fr.iscpif.gridscale.jobservice._
import fr.iscpif.gridscale.jobservice.{ Failed ⇒ GSFailed }

import fr.iscpif.gridscale._
import java.io.File
import org.mockito.Mockito._
import org.scalatest.junit._
import org.scalatest._
import org.scalatest.mock
import org.scalatest.mock.MockitoSugar

// must do a mock per jobservice because of type alias JobService.D :(
class SLURMJobServiceTests extends FunSuite with MockitoSugar {
  test("JobService") {

    val jobService = mock[SLURMJobService]

    val description = new SLURMJobDescription {
      def executable = "/bin/echo"
      def arguments = "success > test_success.txt"
      def workDirectory = "/homes/toto/"
    }

    when(jobService.submit(description)).thenReturn(SLURMJobService.SLURMJob(description, "42"))

    val job = jobService.submit(description)

    assert(job.slurmId === "42")
  }
}

trait FeatureSpecStateBehaviours { this: FeatureSpec with GivenWhenThen ⇒
  def matchState(slurmRetCode: Int, slurmState: String, gridscaleState: JobState) {
    scenario(s"A job is in the state ${slurmState} in SLURM (and return code from submit is: ${slurmRetCode})") {

      Given("A job managed through the job service")

      Then("SLURM's \"" + slurmState + "\" state should translate as " + gridscaleState + " in GridScale")
      assert(SLURMJobService.translateStatus(slurmRetCode, slurmState) === gridscaleState)
    }
  }
}

//@RunWith(classOf[JUnitRunner])
class SLURMJobServiceFeatureSpec extends FeatureSpec with GivenWhenThen with FeatureSpecStateBehaviours {

  feature("States returned by the remote SLURM scheduler are analyzed correctly") {

    info("A job can go through several states all along its execution")
    info("Different SLURM states translate to the same generic state in GridScale")

    List("CONFIGURING", "PENDING", "SUSPENDED") foreach {
      slurmState ⇒ scenariosFor(matchState(1, slurmState, Submitted))
    }

    List("RUNNING", "COMPLETING") foreach {
      slurmState ⇒ scenariosFor(matchState(1, slurmState, Running))
    }

    List("COMPLETED", "COMPLETED?") foreach {
      slurmState ⇒ scenariosFor(matchState(1, slurmState, Done))
    }

    List("CANCELLED", "FAILED", "NODE_FAIL", "PREEMPTED", "TIMEOUT", "COMPLETED?") foreach {
      slurmState ⇒ scenariosFor(matchState(2, slurmState, GSFailed))
    }
  }
}
