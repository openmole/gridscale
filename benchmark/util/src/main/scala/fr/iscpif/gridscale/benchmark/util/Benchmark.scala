/**
 * Copyright (C) 2015 Jonathan Passerat-Palmbach
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

package fr.iscpif.gridscale.benchmark
package util

import fr.iscpif.gridscale.jobservice.JobService

trait BenchmarkUtils {

  def withTimer[T](f: ⇒ T) = {
    val before = System.nanoTime()
    val res = f
    val after = System.nanoTime()
    val elapsed = (after - before)
    (res, elapsed / 1000000d)
  }
}

trait BenchmarkConfig {

  def executable = "/bin/sleep"
  def arguments = "1000"
  def workDirectory = "/homes/jpassera/benchmark"
}

trait Benchmark extends BenchmarkConfig with BenchmarkUtils {
  this: JobService ⇒

  type JobDescription = this.D

  val jobDescription: JobDescription
  val nbJobs: Int

  def benchmark(jd: JobDescription)(nbJobs: Int) = {

    println("Submitting jobs...")
    val (jobs, submitTime) = withTimer {
      (1 to nbJobs).par.map(x ⇒ this.submit(jobDescription))
    }

    println(s"Submitted ${nbJobs} jobs in ${submitTime}")

    println("Querying state for jobs...")
    val (states, queryTime) = withTimer {
      jobs.par.map(this.state)
    }
    println(s"Queried state for ${nbJobs} jobs in ${queryTime}")

    println("Cancelling jobs...")
    val (_, cancelTime) = withTimer(jobs.par.map(this.cancel))
    println(s"Cancelled ${nbJobs} jobs in ${cancelTime}")

    println("Purging jobs...")
    // only purging one job, they're all the same
    this.purge(jobs.head)

    println("Done")

    List(submitTime, queryTime, cancelTime)
  }

  def runBenchmark(runs: Int = 1) = for (run ← 1 to runs) yield benchmark(jobDescription)(nbJobs)

  def avgBenchmark(runs: Int) = {
    val res = runBenchmark(runs)
    res.transpose.map(l ⇒ l.foldLeft(0.0)(_ + _)).map(_ / runs)
  }

}
