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

package fr.gridscale.example.ssh

import java.io.File

import fr.iscpif.gridscale.ssh.{ SSHUserPasswordAuthentication, SSHJobDescription, SSHPrivateKeyAuthentication, SSHJobService }
import fr.iscpif.gridscale._

object SSHJobExample extends App {

  val js = new SSHJobService with SSHUserPasswordAuthentication {
    override def user: String = "test"
    override def password: String = "test"
    override def host: String = "localhost"
  }

  val description = new SSHJobDescription {
    override def workDirectory: String = "/tmp/"
    override def arguments: String = "30"
    override def executable: String = "sleep"
  }

  val id = js.submit(description)

  js.untilFinished(id) { println }

  js.purge(id)

}
