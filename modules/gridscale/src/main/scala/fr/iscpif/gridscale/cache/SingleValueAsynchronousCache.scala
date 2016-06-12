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

package fr.iscpif.gridscale.cache

import scala.concurrent.duration.Duration
import scala.util.{ Success, Failure, Try }

object SingleValueAsynchronousCache {

  def apply[T](_expireInterval: Duration)(_compute: () ⇒ T): SingleValueAsynchronousCache[T] =
    apply[T]((t: T) ⇒ _expireInterval)(_compute)

  def apply[T](_expireInterval: T ⇒ Duration)(_compute: () ⇒ T): SingleValueAsynchronousCache[T] =
    new SingleValueAsynchronousCache[T] {
      override def compute(): T = _compute()
      override def expiresInterval(t: T): Duration = _expireInterval(t)
    }
}

trait SingleValueAsynchronousCache[T] extends (() ⇒ T) {

  @volatile private var cached: Option[Try[(T, Long)]] = None
  @volatile private var caching: Option[Thread] = None

  def compute(): T
  def expiresInterval(t: T): Duration

  def apply(): T = synchronized {
    def computeCache(t: Try[T]) =
      Some(t.map { value ⇒ (value, System.currentTimeMillis + expiresInterval(value).toMillis) })

    def refreshThread = {
      val thread = new Thread(new Runnable {
        override def run(): Unit = {
          try {
            val res = Try { compute() }
            cached = computeCache(res)
          } finally caching = None
        }
      })
      thread.setDaemon(true)
      thread.start
      thread
    }

    val values = (caching, cached)

    values match {
      case (_, None) ⇒
        val value = compute()
        cached = computeCache(Success(value))
        value
      case (_, Some(Failure(t))) ⇒
        cached = None
        caching = None
        throw t
      case (None, Some(Success((v, expireTime)))) if expireTime < System.currentTimeMillis ⇒
        caching = Some(refreshThread)
        v
      case (Some(_), Some(Success((v, expireTime)))) ⇒ v
    }
  }

}
