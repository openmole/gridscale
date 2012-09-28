/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package fr.iscpif.gridscale.jobservice

import fr.iscpif.gridscale.authentication._

trait PBSJobService extends JobService {
  type A = SSHAuthentication
  type J = String
  type D
  
  def submit(description: D)(implicit credential: A): J
  def state(job: J)(implicit credential: A): JobState
  def cancel(job: J)(implicit credential: A)
  def purge(job: J)(implicit credential: A)
}
