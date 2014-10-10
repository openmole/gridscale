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

package fr.iscpif.gridscale.jobservice

import java.util.concurrent.atomic.{ AtomicInteger, AtomicLong }

import scala.collection.mutable

trait PassiveQueue <: JobService {

  case class Job(description: D, id: Long)
  case class RunningJob(state: JobState, jobServiceId: jobService.J)

  val jobService: JobService

  type D = jobService.D
  type J = Job

  def slots: Int
  lazy val queue = mutable.TreeSet.empty[Job](Ordering.by(_.id))
  lazy val runningJobs = new mutable.WeakHashMap[Long, RunningJob]

  var queued = 0
  var submitted = 0
  var id = 0L

  private def nextId = synchronized {
    val jobId = id
    id += 1
    jobId
  }

  private def enqueue(job: J) = synchronized {
    queued += 1
    queue += job
  }

  private def dequeue() = synchronized {
    queue.headOption match {
      case Some(qj @ Job(description, id)) ⇒
        queue.remove(qj)
        queued -= 1
        submitJob(description)
      case None ⇒
    }
  }

  def jobIsFinished = synchronized {
    submitted -= 1
    if (slotIsFree) dequeue()
  }

  def slotIsFree = synchronized(submitted < slots)

  def submitJob(description: D) = synchronized {
    val submittedId = jobService.submit(description)
    submitted += 1
    val id = nextId
    runningJobs.put(id, RunningJob(Submitted, submittedId))
  }

  override def submit(description: D): J = synchronized {
    val j = Job(description, nextId)
    if (slotIsFree) submitJob(description)
    else enqueue(j)
    j
  }

  override def state(job: J): JobState = {
    val runningJob: RunningJob =
      synchronized {
        runningJobs.get(job.id) match {
          case None ⇒ if (queue.contains(job)) return Submitted else return Done
          case Some(rj) ⇒ if(rj.state == Failed) return Failed; rj
        }
      }

    val newState = jobService.state(runningJob.jobServiceId)

    synchronized {
      runningJobs.put(job.id, runningJob.copy(state = newState))
      def wasSubmitted = newState == Running || runningJob.state == Submitted
      def isFinished = newState == Done || newState == Failed
      if (isFinished) runningJobs.remove(id)
      if (wasSubmitted && isFinished) jobIsFinished
    }

    newState
  }



  override def cancel(job: J): Unit = {
    val submittedId =
      synchronized {
        runningJobs.get(job.id) match {
          case None ⇒ queue.remove(job); None
          case Some(RunningJob(_, submittedId)) ⇒ Some(submittedId)
        }
      }
      submittedId.foreach(jobService.cancel)
  }

  override def purge(job: J): Unit = {
    val submittedId =
      synchronized {
        runningJobs.remove(job.id) match {
          case None                             ⇒ queue.remove(job); None
          case Some(RunningJob(_, submittedId)) ⇒ Some(submittedId)
        }
    }
    submittedId.foreach(jobService.purge)
  }
}
