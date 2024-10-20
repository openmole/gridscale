package gridscale.qarnot


import gridscale.http
/*
 * Copyright (C) 2023 Romain Reuillon
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
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

object QarnotExample:
  @main def execute =
    val apiKey = scala.io.Source.fromFile("/home/reuillon/.qarnot/key").getLines().next().trim

    val job = JobDescription("echo gridscale")

    http.HTTP.withHTTP:
      println(submit(job, apiKey))
