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

package fr.iscpif.gridscale.example.sge
import fr.iscpif.gridscale._
import fr.iscpif.gridscale.authentication._
import fr.iscpif.gridscale.sge.{ SGEJobDescription, SGEJobService }
import fr.iscpif.gridscale.ssh._

object SubmitEcho extends App {

  val service = SGEJobService("master.domain")(UserPassword("login", "password"))

  val description = new SGEJobDescription {
    def executable = "/bin/echo"
    def arguments = "hello wold"
    def workDirectory = service.home + "/testjob/"
  }

  val j = service.submit(description)

  val s2 = service.untilFinished(j) { println }

  service.purge(j)
}
