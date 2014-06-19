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

trait SingleValueCache[T] extends (() ⇒ T) {
  @transient private var cached: Option[(T, Long)] = None

  def compute(): T
  def expiresIn(t: T): Duration

  def apply(): T = synchronized {
    def cache = {
      val value = compute()
      cached = Some((value, System.currentTimeMillis + expiresIn(value).toMillis))
      value
    }

    cached match {
      case None ⇒ cache
      case Some((v, expireTime)) ⇒
        if (expireTime < System.currentTimeMillis) cache
        else v
    }
  }

  def forceRenewal = cached = None
}
