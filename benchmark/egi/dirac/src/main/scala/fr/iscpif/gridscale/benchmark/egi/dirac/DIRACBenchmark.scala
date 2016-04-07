/*
 * Copyright (C) 28/06/13 Romain Reuillon
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
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package fr.iscpif.gridscale.benchmark.egi.dirac

import java.io.File

import fr.iscpif.gridscale._
import fr.iscpif.gridscale.egi._
import fr.iscpif.gridscale.jobservice._
import fr.iscpif.gridscale.authentication._
import fr.iscpif.gridscale.benchmark.util._

import scala.concurrent.duration._

object DIRACBenchmark {

  def apply(voName: String, certificateLocation: String, password: String)(inNbJobs: Int) = {

    VOMSAuthentication.setCARepository(new File("/home/jopasserat/.openmole/dantalion/CACertificates"))

    val certificate = new File(certificateLocation)
    val p12 = P12Authentication(certificate, password)

    val dirac = DIRACJobService(voName)(p12)
    dirac.delegate(certificate, password)

    new Benchmark {

      implicit val jobService: DIRACJobService = dirac

      override val nbJobs = inNbJobs

      override val jobDescription = new DIRACJobDescription with BenchmarkConfig {

        override def inputSandbox = List()

        override def outputSandbox = List()
      }
    }
  }
}