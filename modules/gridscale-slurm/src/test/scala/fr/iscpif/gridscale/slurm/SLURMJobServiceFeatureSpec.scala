package fr.iscpif.gridscale.slurm

import fr.iscpif.gridscale.jobservice.{ Failed ⇒ GSFailed, _ }
import org.mockito.Mockito._
import org.scalatest._
import org.scalatest.mock.MockitoSugar

import scala.concurrent.duration._

trait FeatureSpecStateBehaviours { this: FeatureSpec with GivenWhenThen ⇒
  def matchState(slurmRetCode: Int, slurmState: String, gridscaleState: JobState) {
    scenario(s"A job is in the state ${slurmState} in SLURM (and return code from submit is: ${slurmRetCode})") {

      Given("A job managed through the job service")

      Then("SLURM's \"" + slurmState + "\" state should translate as " + gridscaleState + " in GridScale")
      assert(SLURMJobService.translateStatus(slurmRetCode, slurmState) === gridscaleState)
    }
  }
}

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

// must do a mock per jobservice because of type alias JobService.D :(
class SLURMJobServiceTests extends FunSuite with MockitoSugar {
  test("BasicJobService") {

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

class SLURMJobDescriptionTests extends FunSuite {

  val completeDescription = new SLURMJobDescription {
    def executable = "/bin/echo"
    def arguments = "success > test_success.txt"
    def workDirectory = "/homes/toto/"
    override def queue = Some("myPartition")
    override def wallTime = Some(20 minutes)
    override def memory = Some(2048)
    override def nodes = Some(4)
    override def coresByNode = Some(8)
    override def output = "foo.out"
    override def error = "foo.err"
    override def qos = Some("SuperQoS")
    override def gres = List(Gres("gpu", 1))
    override def constraints = List("tesla", "fermi")
  }

  val emptyDescription = new SLURMJobDescription {
    def executable = "/bin/echo"
    def arguments = "success > test_success.txt"
    def workDirectory = "/homes/toto/"
  }

  val slurmPrefix = "#SBATCH"

  val workDirectoryPattern = s"${slurmPrefix} -D "
  test("WorkDirectory (compulsory)") {
    val expectedDescription = (workDirectoryPattern + "/homes/toto").r
    assert(expectedDescription.findFirstIn(completeDescription.toSLURM) != None)
  }

  val queuePattern = s"${slurmPrefix} -p "
  test("Queue specified") {
    val expectedDescription = (queuePattern + "myPartition").r
    assert(expectedDescription.findFirstIn(completeDescription.toSLURM) != None)
  }
  test("Queue empty") {
    val expectedDescription = (queuePattern).r
    assert(expectedDescription.findFirstIn(emptyDescription.toSLURM) === None)
  }

  val timePattern = s"${slurmPrefix} --time="
  test("WallTime specified") {
    val expectedDescription = (timePattern + "00:20:00").r
    assert(expectedDescription.findFirstIn(completeDescription.toSLURM) != None)
  }
  test("WallTime empty") {
    val expectedDescription = (timePattern).r
    assert(expectedDescription.findFirstIn(emptyDescription.toSLURM) === None)
  }

  val memoryPattern = s"${slurmPrefix} --mem="
  test("Memory specified") {
    val expectedDescription = (memoryPattern + "2048").r
    assert(expectedDescription.findFirstIn(completeDescription.toSLURM) != None)
  }
  test("Memory empty") {
    val expectedDescription = (memoryPattern).r
    assert(expectedDescription.findFirstIn(emptyDescription.toSLURM) === None)
  }

  val nodesPattern = s"${slurmPrefix} --nodes="
  test("Nodes specified") {
    val expectedDescription = (nodesPattern + "4").r
    assert(expectedDescription.findFirstIn(completeDescription.toSLURM) != None)
  }
  test("Nodes empty") {
    val expectedDescription = (nodesPattern).r
    assert(expectedDescription.findFirstIn(emptyDescription.toSLURM) === None)
  }

  val coresByNodePattern = s"${slurmPrefix} --cpus-per-task="
  test("CoresByNode specified") {
    val expectedDescription = (coresByNodePattern + "8").r
    assert(expectedDescription.findFirstIn(completeDescription.toSLURM) != None)
  }
  test("CoresByNode empty") {
    val expectedDescription = (coresByNodePattern + "1").r
    assert(expectedDescription.findFirstIn(emptyDescription.toSLURM) != None)
  }

  val outputPattern = s"${slurmPrefix} -o "
  test("Output file specified") {
    val expectedDescription = (outputPattern + "foo.out").r
    assert(expectedDescription.findFirstIn(completeDescription.toSLURM) != None)
  }
  test("Output file empty") {
    val expectedDescription = (outputPattern + "[0-9a-z-]+.out").r
    assert(expectedDescription.findFirstIn(emptyDescription.toSLURM) != None)
  }

  val errorPattern = s"${slurmPrefix} -e "
  test("Error file specified") {
    val expectedDescription = (errorPattern + "foo.err").r
    assert(expectedDescription.findFirstIn(completeDescription.toSLURM) != None)
  }
  test("Error file empty") {
    val expectedDescription = (errorPattern + "[0-9a-z-]+.err").r
    assert(expectedDescription.findFirstIn(emptyDescription.toSLURM) != None)
  }

  val qosPattern = s"${slurmPrefix} --qos="
  test("QoS specified") {
    val expectedDescription = (qosPattern + "SuperQoS").r
    assert(expectedDescription.findFirstIn(completeDescription.toSLURM) != None)
  }
  test("QoS empty") {
    val expectedDescription = (qosPattern).r
    assert(expectedDescription.findFirstIn(emptyDescription.toSLURM) === None)
  }

  val gresPattern = s"${slurmPrefix} --gres="
  test("GRes specified") {
    val expectedDescription = (gresPattern + "gpu:1").r
    assert(expectedDescription.findFirstIn(completeDescription.toSLURM) != None)
  }
  test("GRes empty") {
    val expectedDescription = (gresPattern).r
    assert(expectedDescription.findFirstIn(emptyDescription.toSLURM) === None)
  }

  val constraintsPattern = s"${slurmPrefix} --constraint="
  test("Constraints specified") {
    val expectedDescription = (constraintsPattern + "\"tesla&fermi\"").r
    assert(expectedDescription.findFirstIn(completeDescription.toSLURM) != None)
  }
  test("Constraints empty") {
    val expectedDescription = (constraintsPattern).r
    assert(expectedDescription.findFirstIn(emptyDescription.toSLURM) === None)
  }
}
