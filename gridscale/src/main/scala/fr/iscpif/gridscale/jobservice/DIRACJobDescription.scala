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

package fr.iscpif.gridscale.jobservice

import spray.json.{ JsArray, JsString, JsObject }
import java.io.File

trait DIRACJobDescription extends JobDescription {

  def stdOut: Option[String] = None
  def stdErr: Option[String] = None
  def inputSandbox: Iterable[File]
  def outputSandbox: Iterable[(String, File)]

  def toJSON = {

    def inputSandboxArray = JsArray(inputSandbox.map(f ⇒ JsString(f.getName)).toSeq: _*)
    def outputSandboxArray = JsArray(outputSandbox.map(f ⇒ JsString(f._1)).toSeq: _*)

    val fields = Seq(
      "Executable" -> JsString(executable),
      "Arguments" -> JsString(arguments)) ++
      stdOut.map(s ⇒ "StdOut" -> JsString(s)) ++
      stdErr.map(s ⇒ "StdErr" -> JsString(s)) ++
      (if (!inputSandbox.isEmpty) Some("InputSandbox" -> inputSandboxArray) else None) ++
      (if (!outputSandbox.isEmpty) Some("OutputSandbox" -> outputSandboxArray) else None)

    JsObject(fields: _*).compactPrint
  }

}
