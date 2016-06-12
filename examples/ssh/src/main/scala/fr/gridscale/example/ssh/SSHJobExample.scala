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

import fr.iscpif.gridscale._
import fr.iscpif.gridscale.authentication._
import fr.iscpif.gridscale.ssh._

object SSHJobExample extends App {

  val js = SSHJobService("localhost")(UserPassword("test", "test"))

  val description = SSHJobDescription(
    workDirectory = "/tmp/",
    arguments = "30",
    executable = "sleep"
  )

  val id = js.submit(description)

  js.untilFinished(id) { println }

  js.purge(id)

}
