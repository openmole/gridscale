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

import fr.iscpif.gridscale.dirac._
import fr.iscpif.gridscale._
import java.io.File

object Main extends App {

  implicit val auth = new P12HTTPSAuthentication {
    def certificate = new File("/path/to/certificate.p12")
    def password = "password"
  }

  val jobDesc = new DIRACJobDescription {
    def executable = "/bin/echo"
    def arguments = "hello"
    def inputSandbox = List.empty
  }

  val js = new DIRACJobService {
    def group = "biomed_user"
    def service = "https://ccdirac06.in2p3.fr:9178"
  }

  val j = js.submit(jobDesc)

  untilFinished {
    Thread.sleep(5000)
    val s = js.state(j)
    println(s)
    s
  }

}
