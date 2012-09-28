/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package fr.iscpif.gridscale.jobservice

trait JobDescription {
  def executable: String
  def arguments: String
}
