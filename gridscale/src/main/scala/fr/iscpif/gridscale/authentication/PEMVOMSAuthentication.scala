/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package fr.iscpif.gridscale.authentication

import org.glite.voms.contact.VOMSProxyInit

trait PEMVOMSAuthentication extends VOMSAuthentication {
  
  def certificate: String
  def key: String
  
  def proxyInit(passphrase: String) = VOMSProxyInit.instance(certificate, key, passphrase)
}
