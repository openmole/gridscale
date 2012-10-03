/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package fr.iscpif.gridscale.jobservice

import java.util.UUID
import fr.iscpif.gridscale.tools._

trait PBSJobDescription extends JobDescription {
  val uniqId = UUID.randomUUID.toString
  def workDirectory: String
  def queue: Option[String] = None
  def cpuTime: Option[Int] = None
  def memory: Option[Int] = None
  def output: String = uniqId + ".out"
  def error: String = uniqId + ".err"
  
  def toPBS =  {
    val buffer = new ScriptBuffer
    buffer += "#!/bin/bash"
    
    buffer += "#PBS -o " + output
    buffer += "#PBS -e " + error

    queue match {
      case Some(q) => buffer += "#PBS -q " + q
      case None =>
    }
    
    memory match {
      case Some(m) => buffer += "#PBS -lmem=" + m + "mb"
      case None =>
    }
    
    cpuTime match {
      case Some(t) => 
        val df = new java.text.SimpleDateFormat("HH:mm:ss")
        df.setTimeZone(java.util.TimeZone.getTimeZone("GMT"))
        buffer += "#PBS -lwalltime=" + df.format(t * 1000)
      case None => 
    }
    
    buffer += "cd " + workDirectory
    
    buffer += executable +  " " + arguments
    buffer.toString
  }
}
