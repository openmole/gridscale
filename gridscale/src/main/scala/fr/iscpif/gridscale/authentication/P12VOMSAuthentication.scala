/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package fr.iscpif.gridscale.authentication

import java.io.File
import org.glite.voms.contact.VOMSProxyInit

trait P12VOMSAuthentication extends VOMSAuthentication {
  def certificate: File
  def proxyInit(passphrase: String) = VOMSProxyInit.instance(certificate, passphrase)
}
