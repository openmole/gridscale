/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package fr.iscpif.gridscale.jobservice

import fr.iscpif.gridscale.tools._
import ch.ethz.ssh2.ChannelCondition
import ch.ethz.ssh2.Connection
import fr.iscpif.gridscale.storage.SSHStorage
import java.io.ByteArrayOutputStream
import java.util.UUID

object SSHJobService {
  
  val rootDir = ".gridscale/ssh"
  
  def file(jobId: String, suffix: String) = rootDir + "/" + jobId + "." + suffix
  def pidFile(jobId: String) = file(jobId, "pid")
  def endCodeFile(jobId: String) = file(jobId, "end")
  def outFile(jobId: String) = file(jobId, "out")
  def errFile(jobId: String) = file(jobId, "err")
  
  val PROCESS_CANCELED = 143
  val COMMAND_NOT_FOUND = 127
  
  def exec (connection: Connection, cde: String) = {
    val session = connection.openSession
    try {
      session.execCommand(cde)
      session.waitForCondition(ChannelCondition.EXIT_STATUS, 0)
      if(session.getExitStatus != 0) throw new RuntimeException("Return code was no 0 but " + session.getExitStatus)
    } finally session.close
  }
  
  private class ShellScriptBuffer  {
    var script = ""
    val EOL = "\n"
    
    def +=(s: String) {  script += s + EOL }
    
    override def toString = script
  }
  
}

import SSHJobService._

trait SSHJobService extends JobService with SSHHost with SSHStorage { js =>
  type J = String
  type D = SSHJobDescription
  
  def submit(description: D)(implicit credential: A): J = {
    val jobId = UUID.randomUUID.toString
    val command = new ShellScriptBuffer
      
    def absolute(path: String) = "$HOME/" + path
    
    command += "mkdir -p " + absolute(rootDir)
    command += "mkdir -p " + description.workDirectory
    command += "cd " + description.workDirectory
      
    val executable =  description.executable + " " + description.arguments
      
    val jobDir = 
      command += "((" +
    executable +
    " > " + absolute(outFile(jobId)) + " 2> " + absolute(errFile(jobId)) +" ; " +
    " echo $? > " + absolute(endCodeFile(jobId)) + ") & " +
    "echo $! > " + absolute(pidFile(jobId)) + " )"
      
    withConnection { c => exec(c, "bash -c '" + command.toString + "'") }
    jobId
  }
  
  def state(job: J)(implicit credential: A): JobState = { 
    try {
      val content = Array.ofDim[Byte](256)
      val is = openInputStream(endCodeFile(job))
      val read = 
        try is.read(content)
        finally is.close
      
      translateState(new String(content.slice(0, read)).replaceAll("[\\r\\n]", "").toInt)
    } catch {
      case e => 
        if(!exists(endCodeFile(job))) Running
        else throw e
    } 
  }
  
  def cancel(jobId: J)(implicit credential: A) = withConnection {
    c =>
    val cde = "kill `cat " + pidFile(jobId) + "`;"
    exec(c, cde)
  }

  def purge(jobId: String)(implicit credential: A) = withConnection {
    c =>
    val cde = "rm -rf " + rootDir + "/" + jobId + "*"
    exec(c, cde)
  }
  
  private def translateState(retCode: Int) =  
    if(retCode >= 0)
      if(retCode == PROCESS_CANCELED) Failed
      else if(retCode == COMMAND_NOT_FOUND) Failed
           else Done
    else Failed
  
}
