/*
 * Copyright (C) 06/06/13 Romain Reuillon
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package fr.iscpif.gridscale

import scala.collection.mutable

trait Cache[K, T] {

  def compute(k: K): T
  def cacheTime(t: T): Option[Long]
  def margin: Long = 0

  case class Cached(value: T, time: Long = System.currentTimeMillis) {
    def expiresTime = cacheTime(value).map(_ + time - margin).getOrElse(Long.MaxValue)
  }

  @transient val cache = new mutable.WeakHashMap[K, Cached]

  def apply(k: K): T = synchronized {
    cache.get(k) match {
      case Some(cached) ⇒
        if (cached.expiresTime < System.currentTimeMillis) computeCache(k) else cached.value
      case None ⇒ computeCache(k)
    }
  }

  def get(k: K) = cache.get(k).map(_.value)

  def forceRenewal(k: K) = computeCache(k)

  def computeCache(k: K): T = synchronized {
    val v = compute(k)
    cache(k) = Cached(v)
    v
  }

}
