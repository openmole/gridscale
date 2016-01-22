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

import java.io.File

import fr.iscpif.gridscale.authentication._
import fr.iscpif.gridscale.egi._

import scala.concurrent.duration._

object SRMExample extends App {

  VOMSAuthentication.setCARepository(new File("/path/to/certificates/dir"))

  val p12 = P12Authentication(new File("/path/to/globus/certificate.p12"), "password")
  val authentication = P12VOMSAuthentication(p12, 12 hours, Seq("voms://voms.hellasgrid.gr:15160/C=GR/O=HellasGrid/OU=hellasgrid.gr/CN=voms.hellasgrid.gr"), "vo.complex-systems.eu")

  val bdii = new BDII("ldap://topbdii.grif.fr:2170")
  val srm = bdii.querySRMLocations("biomed", 2 minutes).map { l ⇒ SRMStorage(l)(authentication) }.head
  srm.list("/")

}
