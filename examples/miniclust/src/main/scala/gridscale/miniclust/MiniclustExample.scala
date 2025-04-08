package gridscale.miniclust

/*
 * Copyright (C) 2025 Romain Reuillon
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
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import gridscale.authentication.*
import gridscale.*

@main def miniclustExample =
  val authentication = UserPassword("test2", "test2test2")
  val localhost = MiniclustServer("https://localhost:9000", authentication, insecure = true)

  val jobDescription = MinclustJobDescription("""hostname""", stdOut = Some("out.txt"))

  def res(using Miniclust) =
    val job = submit(jobDescription)
    val s = waitUntilEnded(() => state(job))
    val out = stdOut(job)
    clean(job)
    (s, out)

  Miniclust.withMiniclust(localhost):
    println(res._2.get)



