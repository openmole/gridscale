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

import java.io.File
import fr.iscpif.gridscale._
import fr.iscpif.gridscale.authentication.P12Authentication
import fr.iscpif.gridscale.egi._

import scala.concurrent.duration._

object DiracExample extends App {

  val certificate = new File("/home/reuillon/.globus/certificate.p12")
  val password = "password"

  val p12 = P12Authentication(certificate, password)
  val js = DiracJobService("vo.complex-systems.eu")(p12)

  js.delegate(certificate, password)
  val jobDesc = DIRACJobDescription(
    executable = "/bin/echo",
    arguments = "hello",
    outputSandbox = Seq(
      "out" -> new File("/tmp/diractout.txt"),
      "err" -> new File("/tmp/diracterr.txt")),
    stdOut = Some("out"),
    stdErr = Some("err")
  )

  val j = js.submit(jobDesc)
  js.untilFinished(j, sleepTime = 0 second) { println }
  js.downloadOutputSandbox(jobDesc, j)
  js.delete(j)

}
