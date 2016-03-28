/*
 * Copyright (C) 2015 Jonathan Passerat-Palmbach
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

package fr.iscpif.gridscale.benchmark
package pbs

import java.io.File
import fr.iscpif.gridscale.authentication._
import fr.iscpif.gridscale.pbs._
import fr.iscpif.gridscale.benchmark.util._

import scala.concurrent.duration._

object PBSBenchmark {

  def apply(inHost: String, inUsername: String, inPassword: String, inPrivateKeyPath: String)(inNbJobs: Int) = {

    new Benchmark {

      def credential = PrivateKey(inUsername, new File(inPrivateKeyPath), inPassword)

      implicit val jobService: PBSJobService = PBSJobService(inHost)(credential)

      override val nbJobs = inNbJobs
      override val jobDescription = new PBSJobDescription with BenchmarkConfig {
        override val wallTime = Some(30 minutes)
      }
    }
  }
}
