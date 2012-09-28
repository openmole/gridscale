/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package fr.iscpif.gridscale.jobservice

trait JobService {
  type A
  type J
  type D
  
  def submit(description: D)(implicit credential: A): J
  def state(job: J)(implicit credential: A): JobState
  def cancel(job: J)(implicit credential: A)
  def purge(job: J)(implicit credential: A)
  
}
