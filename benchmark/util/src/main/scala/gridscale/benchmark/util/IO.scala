/**
 * Copyright (C) 2018 Jonathan Passerat-Palmbach
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
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

package gridscale.benchmark.util

import gridscale.benchmark.Benchmark.BenchmarkResults

object IO {

  case class BenchmarkArgs(host: String, username: String, password: String, privateKeyPath: String, nbJobs: Int, nbRuns: Int)

  def parseArgs(argv: Array[String]) =
    argv match {
      case Array(h, u, p, pKP, nbJ, nbR) ⇒ BenchmarkArgs(h, u, p, pKP, nbJ.toInt, nbR.toInt)
      case Array(h, u, p, pKP)           ⇒ BenchmarkArgs(h, u, p, pKP, 10, 10)
      case _                             ⇒ throw new RuntimeException("Bad arguments")
    }

  def format(res: BenchmarkResults, nbJobs: Int, nbRuns: Int) = {
    import res._

    s"""Average for $nbJobs jobs along $nbRuns runs (milliseconds):
       |\tsubmit: $avgSubmit
       |\tstate: $avgState
       |\tcancel: $avgCancel
     """.stripMargin
  }

}
