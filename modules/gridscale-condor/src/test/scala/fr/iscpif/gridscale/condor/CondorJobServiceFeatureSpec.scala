/*
 * Copyright (C) 2015 Jonathan Passerat-Palmbach
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package fr.iscpif.gridscale.condor

import fr.iscpif.gridscale.jobservice.{ Failed ⇒ GSFailed, _ }
import org.mockito.Mockito._
import org.scalatest._
import org.scalatest.mock.MockitoSugar

trait FeatureSpecStateBehaviours { this: FeatureSpec with GivenWhenThen ⇒
  def matchState(condorState: String, gridscaleState: JobState) {
    scenario(s"A job is in the state ${condorState} in Condor") {

      Given("A job managed through the job service")

      Then("Condor's \"" + condorState + "\" state should translate as " + gridscaleState + " in GridScale")
      assert(CondorJobService.translateStatus(condorState) === gridscaleState)
    }
  }
}

class CondorJobServiceFeatureSpec extends FeatureSpec with GivenWhenThen with FeatureSpecStateBehaviours {

  feature("States returned by the remote Condor scheduler are analysed correctly") {

    info("A job can go through several states all along its execution")
    info("Different Condor states translate to the same generic state in GridScale")

    List("0", "1", "5") foreach {
      condorState ⇒ scenariosFor(matchState(condorState, Submitted))
    }

    List("2") foreach {
      condorState ⇒ scenariosFor(matchState(condorState, Running))
    }

    List("3", "4") foreach {
      condorState ⇒ scenariosFor(matchState(condorState, Done))
    }

    List("6") foreach {
      condorState ⇒ scenariosFor(matchState(condorState, GSFailed))
    }
  }
}

// must do a mock per jobservice because of type alias JobService.D :(
class CondorJobServiceTests extends FunSuite with MockitoSugar {
  test("BasicJobService") {

    val jobService = mock[CondorJobService]

    val description = CondorJobDescription (
      executable = "/bin/echo",
      arguments = "success > test_success.txt",
      workDirectory = "/homes/toto/"
    )

    when(jobService.submit(description)).thenReturn(CondorJobService.CondorJob(description, "42"))

    val job = jobService.submit(description)

    assert(job.condorId === "42")
  }
}

class CondorJobDescriptionTests extends FunSuite {

  val completeDescription = CondorJobDescription (
    executable = "/bin/echo",
    arguments = "success > test_success.txt",
    workDirectory = "/homes/toto/",
    memory = Some(2048),
    nodes = Some(4),
    // TODO rename in job description
    coreByNode = Some(8),
    requirements = """JavaVersion == "1.7.0_03"""" &&
      ("""OpSysShortName == "Ubuntu"""" &&
        ("OpSysMajorVer == 14" || "OpSysMajorVer == 12" || "OpSysMajorVer == 13")
      )
  )

  val emptyDescription = CondorJobDescription (
    executable = "/bin/echo",
    arguments = "success > test_success.txt",
    workDirectory = "/homes/toto/"
  )

  val workDirectoryPattern = "initialdir = "
  test("WorkDirectory (compulsory)") {
    val expectedDescription = (workDirectoryPattern + "/homes/toto").r
    assert(expectedDescription.findFirstIn(completeDescription.toCondor) != None)
  }

  val memoryPattern = "request_memory = "
  test("Memory specified") {
    val expectedDescription = (memoryPattern + "2048").r
    assert(expectedDescription.findFirstIn(completeDescription.toCondor) != None)
  }
  test("Memory empty") {
    val expectedDescription = (memoryPattern).r
    assert(expectedDescription.findFirstIn(emptyDescription.toCondor) === None)
  }

  val nodesPattern = "machine_count = "
  test("Nodes specified") {
    val expectedDescription = (nodesPattern + "4").r
    assert(expectedDescription.findFirstIn(completeDescription.toCondor) != None)
  }
  test("Nodes empty") {
    val expectedDescription = (nodesPattern).r
    assert(expectedDescription.findFirstIn(emptyDescription.toCondor) === None)
  }

  val coresByNodePattern = "request_cpus = "
  test("CoresByNode specified") {
    val expectedDescription = (coresByNodePattern + "8").r
    assert(expectedDescription.findFirstIn(completeDescription.toCondor) != None)
  }
  test("CoresByNode empty") {
    val expectedDescription = (coresByNodePattern).r
    assert(expectedDescription.findFirstIn(emptyDescription.toCondor) === None)
  }


  val requirementsPattern = "requirements = "
  test("Requirements specified") {
    // escape brackets to match regexp
    val expectedDescription = (requirementsPattern +
      """\( \( JavaVersion == "1.7.0_03" \) &&
        |\( \( OpSysShortName == "Ubuntu" \) &&
        |\( \( \( OpSysMajorVer == 14 \) || \( OpSysMajorVer == 12 \) \)
        || \( OpSysMajorVer == 13 \) \) \) \)""").r
    assert(expectedDescription.findFirstIn(completeDescription.toCondor) != None)
  }
  test("Requirements empty") {
    val expectedDescription = (requirementsPattern).r
    assert(expectedDescription.findFirstIn(emptyDescription.toCondor) === None)
  }
}
