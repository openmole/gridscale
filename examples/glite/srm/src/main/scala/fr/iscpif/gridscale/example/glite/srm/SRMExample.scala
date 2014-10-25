/*
 * Copyright (C) 2014 Romain Reuillon
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

package fr.iscpif.gridscale.example.glite.srm

import fr.iscpif.gridscale.glite._
import java.io.File
import fr.iscpif.gridscale._
import concurrent.duration._

object SRMExample extends App {

  VOMSAuthentication.setCARepository(new File("/path/to/certificates/dir"))

  implicit val auth = new P12VOMSAuthentication {
    def serverURL = "voms://cclcgvomsli01.in2p3.fr:15000/O=GRID-FR/C=FR/O=CNRS/OU=CC-IN2P3/CN=cclcgvomsli01.in2p3.fr"
    def voName = "biomed"
    def fquan = None
    def lifeTime = 24 hours
    def certificate = new File("/path/to/certificate.p12")
    def password = "password"
  }.cache(1 hour)

  val bdii = new BDII("ldap://topbdii.grif.fr:2170")
  val srm = bdii.querySRMs("biomed", 2 minutes).head

  srm.list("/")

}
