/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package fr.iscpif.gridscale.authentication

import ch.ethz.ssh2.Connection

trait SSHUserPasswordAuthentication extends SSHAuthentication with UserPassword {

  def authenticate(c: Connection) = 
    if(!c.authenticateWithPassword(user, password)) throw new RuntimeException("Authentication failed.")
  
}
