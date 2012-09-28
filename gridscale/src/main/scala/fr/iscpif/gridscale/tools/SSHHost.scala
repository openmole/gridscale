/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package fr.iscpif.gridscale.tools

import ch.ethz.ssh2.SFTPv3Client
import fr.iscpif.gridscale.authentication._
import ch.ethz.ssh2.Connection

trait SSHHost {
  type A = SSHAuthentication
  
  def user: String
  def host: String
  def port: Int = 22
  
  def connectionCache = ConnectionCache.default
  
  def withConnection[T](f: Connection => T)(implicit authentication: SSHAuthentication) = {
    val connection = connectionCache.cached(this)
    try f(connection)
    finally connectionCache.release(this)
  }

  def connect(implicit authentication: SSHAuthentication) = {
    val c = new Connection(host, port)
    c.connect
    authentication.authenticate(c)
    c
  }
  
  def withSftpClient[T](f: SFTPv3Client => T)(implicit authentication: SSHAuthentication): T = withConnection {
    connection =>
    val sftpClient = new SFTPv3Client(connection)
    try f(sftpClient) finally sftpClient.close
  }
}
