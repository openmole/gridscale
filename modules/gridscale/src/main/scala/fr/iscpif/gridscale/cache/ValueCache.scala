/**
 * Created by Romain Reuillon on 12/06/16.
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
package fr.iscpif.gridscale.cache

import scala.concurrent.duration._
import scala.concurrent.stm._

object ValueCache {

  def apply[T](_expireInterval: T ⇒ Duration)(_compute: () ⇒ T): ValueCache[T] =
    new ValueCache[T] {
      override def compute(): T = _compute()
      override def expiresInterval(t: T): Duration = _expireInterval(t)
    }

  def apply[T](_expireInterval: Duration)(_compute: () ⇒ T): ValueCache[T] =
    apply[T]((t: T) ⇒ _expireInterval)(_compute)

}

trait ValueCache[T] extends (() ⇒ T) {
  @volatile private var cached: Option[(T, Long)] = None

  def compute(): T
  def expiresInterval(t: T): Duration

  override def apply(): T = synchronized {
    cached match {
      case None ⇒
        val value = compute()
        cached = Some((value, System.currentTimeMillis + expiresInterval(value).toMillis))
        value
      case Some((v, expireTime)) if expireTime < System.currentTimeMillis ⇒
        val value = compute()
        cached = Some((value, System.currentTimeMillis + expiresInterval(value).toMillis))
        value
      case Some((v, _)) ⇒ v
    }
  }
}

object AsyncValueCache {

  def apply[T](_expireInterval: Duration)(_compute: () ⇒ T): AsyncValueCache[T] =
    new AsyncValueCache[T] {
      override def expiresInterval(t: T): Duration = _expireInterval
      override def compute(): T = _compute()
    }

}

trait AsyncValueCache[T] extends (() ⇒ T) {
  private val cached = Ref(None: Option[(T, Long)])
  private val refreshing = Ref(false)

  def compute(): T
  def expiresInterval(t: T): Duration

  override def apply(): T = atomic { implicit ctx ⇒
    def refresh = {
      refreshing() = true
      try {
        val value = compute()
        cached() = Some((value, System.currentTimeMillis + expiresInterval(value).toMillis))
        value
      } finally refreshing() = false
    }

    cached() match {
      case None ⇒
        if (refreshing()) retry
        else refresh
      case Some((v, expireTime)) if expireTime < System.currentTimeMillis ⇒
        if (refreshing()) v
        else refresh
      case Some((v, _)) ⇒ v
    }
  }

}