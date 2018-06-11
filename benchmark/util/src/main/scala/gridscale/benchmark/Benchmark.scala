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

package gridscale.benchmark

import gridscale.benchmark.util.Time.withTimer
import gridscale.cluster.BatchScheduler.BatchJob
import gridscale.cluster.HeadNode

trait Benchmark[S, D] {
  def submit(server: S, jobDescription: D): BatchJob
  def state(server: S, job: BatchJob): gridscale.JobState
  def clean(server: S, job: BatchJob): Unit
}

object Benchmark {

  def benchmark[S, D](server: S)(jobDescription: D, nbJobs: Int, missingValue: Double = -1.0)(implicit hn: HeadNode[S], bench: Benchmark[S, D]) = {

    import bench._

    println("Submitting jobs...")
    val (jobs, submitTime) = withTimer {
      for (_ ← 1 to nbJobs) yield submit(server, jobDescription)
    }.getOrElse(Seq.empty, missingValue)

    println(s"Submitted $nbJobs jobs in $submitTime")

    println("Querying state for jobs...")

    val (states, queryTime) = withTimer {
      for (job ← jobs) yield state(server, job)
    }.getOrElse(Seq.empty, missingValue)

    println(s"Queried state for ${states.length} jobs in $queryTime")

    println("Cancelling jobs...")

    val (_, cancelTime) = withTimer {
      for (job ← jobs) yield clean(server, job)
    }.getOrElse(Seq.empty, missingValue)

    println(s"Cancelled $nbJobs jobs in $cancelTime")

    println("Benchmark completed")

    List(submitTime, queryTime, cancelTime)
  }

  case class BenchmarkResults(avgSubmit: Double, avgState: Double, avgCancel: Double)

  def avgBenchmark[S, D](server: S)(jobDescription: D, nbJobs: Int, runs: Int, missingValue: Double = -1.0)(implicit hn: HeadNode[S], bench: Benchmark[S, D]) = {

    val res = for (_ ← 1 to runs) yield benchmark(server)(jobDescription, nbJobs)

    res.transpose.map(l ⇒ l.filter(_ != missingValue).foldLeft(0.0)(_ + _)).map(_ / runs) match {
      case IndexedSeq(a, b, c) ⇒ BenchmarkResults(a, b, c)
    }

  }
}
