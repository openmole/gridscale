/*
 * Copyright (C) 2012 Romain Reuillon
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

package fr.iscpif

import fr.iscpif.gridscale.{ cache ⇒ gscache }
import scala.concurrent.duration._
import fr.iscpif.gridscale.jobservice._

package object gridscale {

  def untilFinished(f: ⇒ JobState): JobState = {
    val s = f
    if (s == Done || s == Failed) s
    else untilFinished(f)
  }

  implicit class JobServiceDecorator[T](js: JobService { type J = T }) {
    def waitFinished(job: T, sleepTime: Duration = 5 seconds) = untilFinished(job, sleepTime) { identity }

    def untilFinished(job: T, sleepTime: Duration = 5 seconds)(f: JobState ⇒ Any) =
      gridscale.untilFinished {
        val state = js.state(job)
        f(state)
        Thread.sleep(sleepTime.toMillis)
        state
      }
  }

  implicit val nothingImplicit: Unit = Unit

  implicit class RenewDecorator[T](f: () ⇒ T) {
    def cache(time: Duration) = gscache.cache[T](f)(time)
  }

}