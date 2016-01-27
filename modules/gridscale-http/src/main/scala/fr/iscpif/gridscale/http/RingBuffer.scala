/*
 * Copyright (C) 2015 Romain Reuillon
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
package fr.iscpif.gridscale.http

import java.util.concurrent.Semaphore

import scala.reflect.ClassTag

/**
 * Simple efficient ring-buffer with a power-of-2 size.
 */
class RingBuffer[T: ClassTag](val size: Int) {

  private[this] val array = new Array[T](size)

  /*
   * Two counters counting the number of elements ever written and read
   * wrap-around is handled by always looking at differences or masked values
   */
  @volatile private[this] var writeIx = 0
  @volatile private[this] var readIx = 0 // the "oldest" of all read cursor indices, i.e. the one that is most behind

  /**
   * The number of elements currently in the buffer.
   */
  private def count: Int = synchronized {
    val w = writeIx % size
    val r = readIx % size
    val c = if (r <= w) w - r else (w + size) - r
    c
  }

  def isEmpty: Boolean = synchronized { count == 0 }
  private def nonEmpty: Boolean = count > 0
  private def full = count >= (size - 1)

  /**
   * Tries to put the given value into the buffer and returns true if this was successful.
   */
  def tryEnqueue(value: T): Boolean = synchronized {
    if (!full) {
      array(writeIx) = value
      writeIx = (writeIx + 1) % size
      notifyAll()
      true
    } else false
  }

  /**
   * Reads and removes the next value from the buffer.
   * If the buffer is empty the method throws a NoSuchElementException.
   */
  def tryDequeue(): Option[T] = synchronized {
    if (count > 0) {
      val result = array(readIx)
      readIx = (readIx + 1) % size
      notifyAll()
      Some(result)
    } else None
  }

  def waitNotFull = synchronized { if (full) wait(100) }
  def waitEmpty = synchronized { if (!isEmpty) wait(100) }
  def waitNotEmpty = synchronized { if (isEmpty) wait(100) }

}
