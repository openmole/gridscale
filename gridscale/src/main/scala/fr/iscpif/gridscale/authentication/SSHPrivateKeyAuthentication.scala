/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package fr.iscpif.gridscale.authentication

import ch.ethz.ssh2.Connection

trait SSHPrivateKeyAuthentication extends SSHAuthentication with PrivateKey {

  def authenticate(c: Connection) = 
    if (!c.authenticateWithPublicKey(user, privateKey, passphrase)) throw new RuntimeException("Authentication failed.")
  
}
