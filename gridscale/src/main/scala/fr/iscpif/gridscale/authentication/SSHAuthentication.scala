/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package fr.iscpif.gridscale.authentication

import ch.ethz.ssh2.Connection

trait SSHAuthentication {
  
  def connect(host: String, port: Int): Connection = {
    val c = new Connection(host, port)
    c.connect
    authenticate(c)
    c
  }
  
  def authenticate(c: Connection)
}
