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

import _root_.fr.iscpif.gridscale.benchmark.util.Benchmark
import fr.iscpif.gridscale.egi._
import fr.iscpif.gridscale._
import java.io.File
import concurrent.duration._

class WMSBenchmark(val voName: String, val certificateLocation: String, val password: String)(val nbJobs: Int)
    extends Benchmark with WMSJobService with P12VOMSAuthentication { wmsBenchmarkService ⇒

  VOMSAuthentication.setCARepository(new File("/home/jopasserat/.openmole/dantalion/CACertificates"))

  implicit val auth = wmsBenchmarkService.cache(1 hour)

  //  implicit val auth = new P12VOMSAuthentication {
  //    def serverURL = "voms://cclcgvomsli01.in2p3.fr:15000/O=GRID-FR/C=FR/O=CNRS/OU=CC-IN2P3/CN=cclcgvomsli01.in2p3.fr"
  //
  //    def voName = "biomed"
  //
  //    def fquan = None
  //
  //    def lifeTime = 24 hours
  //
  //    def certificate = new File("~/.globus/grid_certificate_uk_LeSC.p12")
  //
  //    def password = "openmole"
  //
  //    //  }.cache(1 hour)
  //  }

  //  def serverURL = auth.serverURL
  //  def voName = auth.voName
  //  def fquan = auth.fquan
  //  def lifeTime = auth.lifeTime
  //  def certificate = auth.certificate
  //  def password = auth.password

  def serverURL = "voms://cclcgvomsli01.in2p3.fr:15000/O=GRID-FR/C=FR/O=CNRS/OU=CC-IN2P3/CN=cclcgvomsli01.in2p3.fr"
  def fquan = None
  def lifeTime = 24 hours
  def certificate = new File(certificateLocation)

  val bdii = new BDII("ldap://topbdii.grif.fr:2170")
  val wms = bdii.queryWMS("biomed", 2 minutes).head
  def url = wms.url
  def credential = wms.credential

  override val jobDescription = new WMSJobDescription {
    val executable = wmsBenchmarkService.executable
    val arguments = wmsBenchmarkService.arguments
    def inputSandbox = List()
    def outputSandbox = List()
    override def fuzzy = true
  }
}
