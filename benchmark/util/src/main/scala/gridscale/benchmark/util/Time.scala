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

object Time {

  import java.lang.{ System ⇒ JSystem }

  def nano2Millis(nanoSeconds: Long) = nanoSeconds / 1000000d

  case class TimedResult[T](optResult: Option[T], runtimeMillis: Double)

  def withTimer[T](f: ⇒ T) = {
    val before = JSystem.nanoTime()
    val res = scala.util.Try(f).toOption
    val after = JSystem.nanoTime()
    val elapsed = after - before
    TimedResult(res, nano2Millis(elapsed))
  }

}
