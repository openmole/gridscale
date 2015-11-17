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

package fr.iscpif.gridscale.exemple.dirac

import fr.iscpif.gridscale.authentication.P12Authentication
import fr.iscpif.gridscale._
import java.io.File
import fr.iscpif.gridscale.egi._

import concurrent.duration._

object Main extends App {

  val p12 = P12Authentication(new File("/path/to/certificate.p12"), "password")

  val jobDesc = new DIRACJobDescription {
    def executable = "/bin/echo"
    def arguments = "hello"
  }

  val js = DIRACJobService(group = "complex_user", service = "https://ccdiracli06.in2p3.fr:9178")(p12)

  println(js.token)

  val j = js.submit(jobDesc)

  js.untilFinished(j, sleepTime = 0 second) { println }

}
