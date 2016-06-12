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
package fr.iscpif.gridscale.egi

import java.io.File

import fr.iscpif.gridscale.jobservice.JobDescription
import spray.json.{ JsArray, JsObject, JsString }

import scala.concurrent.duration.Duration

object DIRACJobDescription {
  val Linux_x86_64_glibc_2_11 = "Linux_x86_64_glibc-2.11"
  val Linux_x86_64_glibc_2_12 = "Linux_x86_64_glibc-2.12"
  val Linux_x86_64_glibc_2_5 = "Linux_x86_64_glibc-2.5"

  val Linux_i686_glibc_2_34 = "Linux_i686_glibc-2.3.4"
  val Linux_i686_glibc_2_5 = "Linux_i686_glibc-2.5"
}

case class DIRACJobDescription(
    executable: String,
    arguments: String,
    stdOut: Option[String] = None,
    stdErr: Option[String] = None,
    inputSandbox: Seq[File] = List.empty,
    outputSandbox: Seq[(String, File)] = List.empty,
    platforms: Seq[String] = Seq.empty,
    cpuTime: Option[Duration] = None,
    group: Option[String] = None) extends JobDescription {

  def toJSON = {
    def inputSandboxArray = JsArray(inputSandbox.map(f ⇒ JsString(f.getName)): _*)
    def outputSandboxArray = JsArray(outputSandbox.map(f ⇒ JsString(f._1)): _*)
    def platformsArray = JsArray(platforms.map(f ⇒ JsString(f)): _*)

    val fields = Seq(
      "Executable" -> JsString(executable),
      "Arguments" -> JsString(arguments)) ++
      stdOut.map(s ⇒ "StdOutput" -> JsString(s)) ++
      stdErr.map(s ⇒ "StdError" -> JsString(s)) ++
      (if (!inputSandbox.isEmpty) Some("InputSandbox" -> inputSandboxArray) else None) ++ (if (!outputSandbox.isEmpty) Some("OutputSandbox" -> outputSandboxArray) else None)
    cpuTime.map(s ⇒ "CPUTime" -> JsString(s.toSeconds.toString)) ++
      (if (!platforms.isEmpty) Some("Platform" -> platformsArray) else None) ++
      group.map(s ⇒ "JobGroup" -> JsString(s))

    JsObject(fields: _*).compactPrint
  }

}