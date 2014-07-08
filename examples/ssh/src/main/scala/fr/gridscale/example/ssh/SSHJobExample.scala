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

import fr.iscpif.gridscale.ssh.{ SSHJobDescription, SSHPrivateKeyAuthentication, SSHJobService }
import fr.iscpif.gridscale._

object SSHJobExample extends App {

  val js = new SSHJobService with SSHPrivateKeyAuthentication {
    override def privateKey: File = new File("/path/to/.ssh/id_dsa")

    override def user: String = "login"

    override def password: String = "keypassword"

    override def host: String = "machine.domain"
  }

  val description = new SSHJobDescription {
    override def workDirectory: String = "/tmp"
    override def arguments: String = "hello world"
    override def executable: String = "echo"
  }

  val id = js.submit(description)

  untilFinished { val s = js.state(id); println(s); s }

  js.purge(id)

}
