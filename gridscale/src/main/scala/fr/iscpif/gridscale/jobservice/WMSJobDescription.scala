/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package fr.iscpif.gridscale.jobservice

trait WMSJobDescription extends JobDescription {
  def inputSandbox: Iterable[String]
  def outputSandbox: Iterable[(String, String)]
  def requirements = "other.GlueCEStateStatus == \"Production\""
  def rank = "(-other.GlueCEStateEstimatedResponseTime)"
  def fuzzy: Boolean = false
  def stdOutput = ""
  def stdError = ""
  
  def toJDL = {
    val sb = new StringBuilder
    sb.append("Executable = \"")
    sb.append(executable)
    sb.append("\";\n")
    sb.append("Arguments = \"")
    sb.append(arguments)
    sb.append("\";\n")
    
    if(!inputSandbox.isEmpty) {
      sb.append("InputSandbox = ")
      sb.append(sandboxTxt(inputSandbox))
    }
    sb.append(";\n")
    
    if(!outputSandbox.isEmpty) {
      sb.append("OutputSandbox = ")
      sb.append(sandboxTxt(outputSandbox.unzip._1))
    }
    sb.append(";\n")
    
    if(!stdOutput.isEmpty) {
      sb.append("StdOutput = \"")
      sb.append(stdOutput)
      sb.append("\";\n")
    }
        
    if(!stdError.isEmpty) {
      sb.append("StdError = \"")
      sb.append(stdError)
      sb.append("\";\n")
    }
    
    sb.append("Requirements = ")
    sb.append(requirements)
    sb.append(";\n")
    sb.append("Rank = ")
    sb.append(rank)
    sb.append(";\n")
    if(fuzzy) sb.append("FuzzyRank = true;\n")
    sb.toString
  }
  
  private def sandboxTxt(sandbox: Iterable[String]) = 
    "{" + sandbox.map{"\"" + _ + "\""}.mkString(",") + "}"
    
  
}
