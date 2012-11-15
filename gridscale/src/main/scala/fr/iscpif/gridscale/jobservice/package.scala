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

package fr.iscpif.gridscale

package object jobservice {

  sealed trait JobState
  case object Submitted extends JobState
  case object Running extends JobState
  case object Done extends JobState
  case object Failed extends JobState

  def untilFinished(f: ⇒ JobState): JobState = {
    val s = f
    if (s == Done || s == Failed) s
    else untilFinished(f)
  }

}