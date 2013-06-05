/*
 * Copyright (C) 05/06/13 Romain Reuillon
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

import java.net.HttpURLConnection
import scala.io.Source

object HttpURLConnectionUtil {

  implicit class HTTPURLConnectionDecorator(c: HttpURLConnection) {
    def getString = {
      c.connect
      if (c.getResponseCode != 200) throw new RuntimeException("Response code is " + c.getResponseCode + " (not 200) " + c.getResponseMessage)
      val s = Source.fromInputStream(c.getInputStream)
      try s.getLines().mkString("\n")
      finally s.close
    }
  }
}
