/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package fr.iscpif.gridscale.authentication

import java.io.File

trait PrivateKey {
  def privateKey: File
  def passphrase: String
  def user: String
}
