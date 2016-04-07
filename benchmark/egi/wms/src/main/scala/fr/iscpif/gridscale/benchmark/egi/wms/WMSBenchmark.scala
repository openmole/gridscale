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

package fr.iscpif.gridscale.benchmark.egi.wms

import java.io.File

import fr.iscpif.gridscale._
import fr.iscpif.gridscale.egi._
import fr.iscpif.gridscale.jobservice._
import fr.iscpif.gridscale.authentication._
import fr.iscpif.gridscale.benchmark.util._

import scala.concurrent.duration._

object WMSBenchmark {

  def apply(voName: String, certificateLocation: String, password: String)(inNbJobs: Int) = {

    VOMSAuthentication.setCARepository(new File("/home/jopasserat/.openmole/dantalion/CACertificates"))

    //  implicit val auth = wmsBenchmarkService.cache(1 hour)
    //
    //  def serverURL = "voms://cclcgvomsli01.in2p3.fr:15000/O=GRID-FR/C=FR/O=CNRS/OU=CC-IN2P3/CN=cclcgvomsli01.in2p3.fr"
    //  def fquan = None
    //  def lifeTime = 24 hours
    //  def certificate = new File(certificateLocation)

    val p12 = P12Authentication(new File(certificateLocation), password)
    val authentication = P12VOMSAuthentication(p12, 24 hours, Seq("voms://cclcgvomsli01.in2p3.fr:15000/O=GRID-FR/C=FR/O=CNRS/OU=CC-IN2P3/CN=cclcgvomsli01.in2p3.fr"), voName)

    val bdii = BDII("topbdii.grif.fr", 2170)

    val wms = bdii.queryWMSLocations(voName).map(l ⇒ WMSJobService(l)(authentication)).head

    //    def url = wms.url
    //    def credential = wms.credential

    new Benchmark {

      implicit val jobService: WMSJobService = wms

      override val nbJobs = inNbJobs

      override val jobDescription = new WMSJobDescription with BenchmarkConfig {
        //      val executable = wmsBenchmarkService.executable
        //      val arguments = wmsBenchmarkService.arguments

        def inputSandbox = List()

        def outputSandbox = List()

        override def fuzzy = true
      }
    }
  }
}