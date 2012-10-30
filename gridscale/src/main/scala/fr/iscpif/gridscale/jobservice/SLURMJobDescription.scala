/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package fr.iscpif.gridscale.jobservice

import java.util.UUID
import fr.iscpif.gridscale.tools._

trait SLURMJobDescription extends JobDescription {
  val uniqId = UUID.randomUUID.toString
  def workDirectory: String
  def queue: Option[String] = None
  def cpuTime: Option[Int] = None
  def memory: Option[Int] = None
  def output: String = uniqId + ".out"
  def error: String = uniqId + ".err"
  
  def toSLURM =  {
    val buffer = new ScriptBuffer
    buffer += "#!/bin/bash"
    
    buffer += "#SBATCH -o " + output
    buffer += "#SBATCH -e " + error

    queue match {
      case Some(p) => buffer += "#SBATCH -p " + p
      case None =>
    }
    
    memory match {
      case Some(m) => buffer += "#SBATCH --mem-per-cpu=" + m
      case None =>
    }
    
    cpuTime match {
      case Some(t) => buffer += "#SBATCH --time=" + t * 60 
      case None => 
    }

    workDirectory match {
      case Some(w) => buffer += "#SBATCH -D " + workDirectory
      case None =>
    }
    
    buffer += "srun " + executable +  " " + arguments
    buffer.toString
  }
}
