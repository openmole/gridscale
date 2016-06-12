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

import java.util.concurrent.{ Executors, Future }
import scala.concurrent.duration.Duration
import scala.util.{ Success, Failure, Try }

trait SingleValueAsynchronousCache[T] extends (() ⇒ T) {

  @transient private var cached: Option[Try[(T, Long)]] = None
  @transient private var caching: Option[Thread] = None

  def compute(): T
  def expiresIn(t: T): Duration

  def apply(): T = synchronized {
    def cache = {
      val value = compute()
      cached = computeCache(Success(value))
      value
    }

    def computeCache(t: Try[T]) =
      Some(t.map { value ⇒ (value, System.currentTimeMillis + expiresIn(value).toMillis) })

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

    (caching, cached) match {
      case (_, None) ⇒ cache
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
