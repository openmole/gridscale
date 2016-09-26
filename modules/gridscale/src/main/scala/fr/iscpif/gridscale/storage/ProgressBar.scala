/**
 * Created by Romain Reuillon on 26/09/16.
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
 *
 */
package fr.iscpif.gridscale.storage

import java.util.concurrent.atomic._

trait Progress {
  def progressBar: ProgressBar
}

class ProgressBar(val size: Long) {
  private val _hasBeenRead = new AtomicLong(0)
  def hasBeenRead = _hasBeenRead.get()
  def read(c: ⇒ Int) = {
    val r = c
    if (r != -1) _hasBeenRead.incrementAndGet()
    r
  }
  def isCompleted = hasBeenRead >= size
}
