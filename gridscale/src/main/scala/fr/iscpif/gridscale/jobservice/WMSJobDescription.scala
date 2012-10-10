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

package fr.iscpif.gridscale.jobservice

import fr.iscpif.gridscale.tools._

trait WMSJobDescription extends JobDescription {
  def inputSandbox: Iterable[String]
  def outputSandbox: Iterable[(String, String)]
  
  def rank = "(-other.GlueCEStateEstimatedResponseTime)"
  def fuzzy: Boolean = false
  def stdOutput = ""
  def stdError = ""
  def memory: Option[Int] = None
  def cpuTime: Option[Int] = None
  def cpuNumber: Option[Int] = None
  def jobType: Option[String] = None
  def smpGranularity: Option[Int] = None
  
  def requirements = 
    "other.GlueCEStateStatus == \"Production\"" + 
    memory.map(" && other.GlueHostMainMemoryRAMSize>" + _) +
    cpuTime.map(" && other.GlueCEPolicyMaxCPUTime>" + _)
  
  def toJDL = {
    val script = new ScriptBuffer
    
    jobType.foreach(script += "JobType = \"" + _ + "\";")
    script += "Executable = \"" + executable + "\";"
    script += "Arguments = \"" + arguments + "\";"
    
    if(!inputSandbox.isEmpty) script += "InputSandbox = " + sandboxTxt(inputSandbox) + ";"
    if(!outputSandbox.isEmpty) script += "OutputSandbox = " + sandboxTxt(outputSandbox.unzip._1) + ";"
    
    if(!stdOutput.isEmpty) script += "StdOutput = \"" + stdOutput + "\";"
    if(!stdError.isEmpty) script += "StdError = \"" + stdError + "\";"
    
    cpuNumber.foreach(script += "CpuNumber = " + _ + ";")
    smpGranularity.foreach(script += "SMPGranularity = " + _ + ";")
    
    script += "Requirements = " + requirements + ";"
    script += "Rank = " + rank + ";"
    
    if(fuzzy) script += "FuzzyRank = true;"
    
    script.toString
  }
  
  private def sandboxTxt(sandbox: Iterable[String]) = 
    "{" + sandbox.map{"\"" + _ + "\""}.mkString(",") + "}"
    
  
}