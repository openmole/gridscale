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

package fr.iscpif.gridscale.tools

import scala.collection.mutable

class Cache[K, T](f: K ⇒ T, cacheTime: T ⇒ Long, margin: Long) {

  case class Cached(value: T, time: Long = System.currentTimeMillis) {
    def expiresTime = time + cacheTime(value) - margin
  }

  @transient val cache = new mutable.WeakHashMap[K, Cached]

  def apply(k: K): T = synchronized {
    if (!cache.contains(k) || cache(k).expiresTime < System.currentTimeMillis) {
      val v = f(k)
      cache(k) = Cached(v)
      v
    } else cache(k).value
  }

}
