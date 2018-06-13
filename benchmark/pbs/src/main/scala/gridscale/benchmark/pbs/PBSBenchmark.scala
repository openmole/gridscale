/*
 * Copyright (C) 2018 Jonathan Passerat-Palmbach
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

package gridscale.benchmark
package pbs

import effectaside.{ Effect, System }
import gridscale.authentication.PrivateKey
import gridscale.pbs._
import gridscale.pbs
import gridscale.ssh._
import gridscale.local._
import gridscale.benchmark.util._
import gridscale.cluster.BatchScheduler.BatchJob
import gridscale.cluster._
import squants.time.TimeConversions._
import squants.information.InformationConversions._

import scala.language.{ higherKinds, postfixOps }

object PBSBenchmark {

  def run[S](server: S)(nbJobs: Int, runs: Int)(implicit system: Effect[System], ssh: Effect[SSH], local: Effect[Local], hn: HeadNode[S]) = {

    // by default flavour = Torque, there's no need to specify it
    val jobDescription = PBSJobDescription("/bin/sleep 1000", "/work/jpassera/test_gridscale", wallTime = Some(10 minutes), memory = Some(2 gigabytes), flavour = PBSPro)

    implicit val benchmark = new Benchmark[PBSJobDescription] {
      override def submit(jobDescription: PBSJobDescription): BatchJob = pbs.submit[S](server, jobDescription)
      override def state(job: BatchJob): gridscale.JobState = pbs.state[S](server, job)
      override def clean(job: BatchJob): Unit = pbs.clean[S](server, job)
    }

    Benchmark.avgBenchmark(server)(jobDescription, nbJobs, runs)
  }

  def main(argv: Array[String]): Unit = {
    val params = IO.parseArgs(argv)

    val res = ClusterInterpreter { intp ⇒
      import intp._

      if (params.host.equals("localhost")) run(LocalHost())(params.nbJobs, params.nbRuns)
      else {
        val authentication = PrivateKey(new java.io.File(params.privateKeyPath), params.password, params.username)
        run(SSHServer(params.host)(authentication))(params.nbJobs, params.nbRuns)
      }
    }

    println(IO.format(res, params.nbJobs, params.nbRuns))
  }
}
