/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package fr.iscpif.gridscale.storage

import java.io.InputStream
import java.io.OutputStream

trait Storage {
  type A
  
  def exists(path: String)(implicit authentication: A) = 
    try {
      list(path)
      true
    } catch {
      case e: Throwable => false
    }
    
  def listNames(path: String)(implicit authentication: A) = list(path).unzip._1
  def list(path: String)(implicit authentication: A): Seq[(String, FileType)]
  def makeDir(path: String)(implicit authentication: A)
  def rmDir(path: String)(implicit authentication: A)
  def rmFile(patg: String)(implicit authentication: A)
  def openInputStream(path: String)(implicit authentication: A): InputStream
  def openOutputStream(path: String)(implicit authentication: A): OutputStream
}
