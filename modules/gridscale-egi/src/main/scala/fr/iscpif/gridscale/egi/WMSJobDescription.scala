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

package fr.iscpif.gridscale.egi

import java.io.File
import fr.iscpif.gridscale.tools.ScriptBuffer

import scala.concurrent.duration.Duration

case class WMSJobDescription(
    executable: String,
    arguments: String,
    inputSandbox: Iterable[File] = List.empty,
    outputSandbox: Iterable[(String, File)] = List.empty,
    rank: String = "(-other.GlueCEStateEstimatedResponseTime)",
    fuzzy: Boolean = false,
    stdOutput: Option[String] = None,
    stdError: Option[String] = None,
    memory: Option[Int] = None,
    cpuTime: Option[Duration] = None,
    cpuNumber: Option[Int] = None,
    wallTime: Option[Duration] = None,
    jobType: Option[String] = None,
    smpGranularity: Option[Int] = None,
    retryCount: Option[Int] = None,
    shallowRetryCount: Option[Int] = None,
    myProxyServer: Option[String] = None,
    architecture: Option[String] = None,
    ce: Option[Iterable[String]] = None,
    extraRequirements: Option[String] = None) {

  def requirements =
    "other.GlueCEStateStatus == \"Production\"" +
      memory.map(" && other.GlueHostMainMemoryRAMSize >= " + _).mkString +
      cpuTime.map(" && other.GlueCEPolicyMaxCPUTime >= " + _.toMinutes).mkString +
      wallTime.map(" && other.GlueCEPolicyMaxWallClockTime >= " + _.toMinutes).mkString +
      architecture.map(" && other.GlueHostArchitecturePlatformType == \"" + _ + "\"").mkString +
      ce.map(" && (" + _.map("other.GlueCEUniqueID == \"" + _ + "\"").mkString("|") + ")").mkString +
      extraRequirements.map(" && " + _)

  def toJDL = {
    val script = new ScriptBuffer

    jobType.foreach(script += "JobType = \"" + _ + "\";")
    script += "Executable = \"" + executable + "\";"
    script += "Arguments = \"" + arguments + "\";"

    if (!inputSandbox.isEmpty) script += "InputSandbox = " + sandboxTxt(inputSandbox.map(_.getPath)) + ";"
    if (!outputSandbox.isEmpty) script += "OutputSandbox = " + sandboxTxt(outputSandbox.unzip._1) + ";"

    stdOutput.foreach(stdOutput ⇒ script += "StdOutput = \"" + stdOutput + "\";")
    stdError.foreach(stdError ⇒ script += "StdError = \"" + stdError + "\";")

    cpuNumber.foreach(script += "CpuNumber = " + _ + ";")
    smpGranularity.foreach(script += "SMPGranularity = " + _ + ";")

    script += "Requirements = " + requirements + ";"
    script += "Rank = " + rank + ";"

    if (fuzzy) script += "FuzzyRank = true;"

    retryCount.foreach(script += "RetryCount = " + _ + ";")
    shallowRetryCount.foreach(script += "ShallowRetryCount = " + _ + ";")

    myProxyServer.foreach(script += "MyProxyServer = \"" + _ + "\";")

    script.toString
  }

  private def sandboxTxt(sandbox: Iterable[String]) =
    "{" + sandbox.map { "\"" + _ + "\"" }.mkString(",") + "}"

}
