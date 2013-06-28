/*
 * Copyright (C) 28/06/13 Romain Reuillon
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

package fr.iscpif.gridscale.exemple.glite

import fr.iscpif.gridscale.glite._
import fr.iscpif.gridscale._
import java.io.File

object Main extends App {

  val bdii = new BDII("ldap://topbdii.grif.fr:2170")
  val wms = bdii.queryWMS("biomed", 120).head

  VOMSAuthentication.setCARepository(new File("/dir/to/you/authority/certificates/dir"))

  implicit val auth = new P12VOMSAuthentication {
    def serverURL = "voms://cclcgvomsli01.in2p3.fr:15000/O=GRID-FR/C=FR/O=CNRS/OU=CC-IN2P3/CN=cclcgvomsli01.in2p3.fr"
    def voName = "biomed"
    def proxyFile = new File("/tmp/proxy.x509")
    def fquan = None
    def lifeTime = 24 * 3600
    def certificate = new File("/path/to/your/certificate.p12")
    def password = "password"
  }.cache(3600)

  val jobDesc = new WMSJobDescription {
    def executable = "/bin/cat"
    def arguments = "testis"
    override def stdOutput = "out.txt"
    override def stdError = "error.txt"
    def inputSandbox = List(new File("/tmp/testis"))
    def outputSandbox = List("out.txt" -> new File("/tmp/out.txt"), "error.txt" -> new File("/tmp/error.txt"))
    override def fuzzy = true
  }

  val j = wms.submit(jobDesc)

  val s = untilFinished {
    Thread.sleep(5000); val s = wms.state(j); println(s); s
  }

  if (s == Done) wms.downloadOutputSandbox(jobDesc, j)
  wms.purge(j)


}
