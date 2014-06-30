/*
 * Copyright (C) 2014 Jonathan Passerat-Palmbach
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

package fr.iscpif.gridscale.sge

import fr.iscpif.gridscale.ssh.{ SSHPrivateKeyAuthentication }

import fr.iscpif.gridscale.jobservice._
import fr.iscpif.gridscale.jobservice.{ Failed ⇒ GSFailed }
import org.scalatest._

import org.scalatest.mock.MockitoSugar
import org.mockito.Mockito._

// must do a mock per jobservice because of type alias JobService.D :(
class SGEJobServiceTests extends FunSuite with MockitoSugar {
  test("JobService") {

    val jobService = mock[SGEJobService]

    val description = new SGEJobDescription {
      def executable = "/bin/echo"
      def arguments = "success > test_success.txt"
      def workDirectory = "/homes/toto/"
    }

    when(jobService.submit(description)).thenReturn(SGEJobService.SGEJob(description, "42"))

    val job = jobService.submit(description)

    assert(job.sgeId === "42")
  }
}

trait FeatureSpecStateBehaviours { this: FeatureSpec with GivenWhenThen ⇒
  def matchState(sgeState: String, gridscaleState: JobState) {
    scenario("A job is in the state " + sgeState + " in SGE") {

      Given("A job managed through the job service")

      Then("SGE's \"" + sgeState + "\" state should translate as " + gridscaleState + " in GridScale")
      assert(SGEJobService.translateStatus(sgeState) === gridscaleState)
    }
  }
}

//@RunWith(classOf[JUnitRunner])
class SGEJobServiceFeatureSpec extends FeatureSpec with GivenWhenThen with FeatureSpecStateBehaviours {

  feature("States returned by the remote SGE scheduler are analyzed correctly") {

    info("A job can go through several states all along its execution")
    info("Different SGE states translate to the same generic state in GridScale")

    List("qw", "hqw", "hRwq", "Rs", "Rts", "RS", "RtS", "RT", "RtT") foreach {
      sgeState ⇒ scenariosFor(matchState(sgeState, Submitted))
    }

    List("r", "t", "Rr", "Rt", "T", "tT", "s", "ts", "S", "tS") foreach {
      sgeState ⇒ scenariosFor(matchState(sgeState, Running))
    }

    List("dr", "dt", "dRr", "dRt", "ds", "dS", "dT", "dRs", "dRS", "dRT") foreach {
      sgeState ⇒ scenariosFor(matchState(sgeState, Done))
    }

    List("Eqw", "Ehqw", "EhRqw") foreach {
      sgeState ⇒ scenariosFor(matchState(sgeState, GSFailed))
    }
  }
}
