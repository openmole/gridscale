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

package fr.iscpif.gridscale.example.oar

import fr.iscpif.gridscale.oar._
import fr.iscpif.gridscale.ssh.{ SSHUserPasswordAuthentication, SSHPrivateKeyAuthentication }
import fr.iscpif.gridscale._

object SubmitEcho extends App {

  implicit val service = new OARJobService with SSHUserPasswordAuthentication {
    def host = "172.17.0.4"
    def user = "docker"
    def password = "docker"
  }

  val description = new OARJobDescription {
    def executable = "/bin/echo"
    def arguments = "hello wold"
    def workDirectory = "/data/"
  }

  val j = service.submit(description)

  val s2 = untilFinished { Thread.sleep(5000); val s = service.state(j); println(s); s }

  service.purge(j)
}
