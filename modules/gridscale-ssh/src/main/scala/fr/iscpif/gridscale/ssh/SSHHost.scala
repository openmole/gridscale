/*
 * Copyright (C) 2012 Romain Reuillon
 * Copyright (C) 2015 Jonathan Passerat-Palmbach
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package fr.iscpif.gridscale.ssh

import net.schmizz.sshj._
import net.schmizz.sshj.sftp._

import scala.concurrent.duration.Duration

import scalaz._, Scalaz._, scalaz.concurrent.Future

trait SSHHost {

  def credential: SSHAuthentication
  def host: String
  def port: Int
  def timeout: Duration

  def withConnection[T](f: SSHClient ⇒ T) = {
    val connection = getConnection
    connection.setConnectTimeout(timeout.toMillis.toInt)
    connection.setTimeout(timeout.toMillis.toInt)
    try f(connection)
    finally release(connection)
  }

  def getConnection = connect
  def release(c: SSHClient) = c.close

  def connect = {
    val ssh = credential.connect(host, port)
    ssh.setConnectTimeout(timeout.toMillis.toInt)
    ssh.setTimeout(timeout.toMillis.toInt)
    ssh
  }

  def withSftpClient[T](f: SFTPClient ⇒ T): T = withConnection {
    connection ⇒
      val sftpClient = connection.newSFTPClient
      try f(sftpClient) finally sftpClient.close
  }

  /**
   * Generic function creating the futures from the action applied to the jobs
   *
   * @param jobs list of Jobs / Job Description to apply an action on
   * @param process Function performing an action on a job through an implicit connection
   * @return A sequence containing the created futures
   */
  def processFutures[E, J, R](jobs: J*)(process: J ⇒ Reader[E, Future[(J, R)]]) = {
    val futures = jobs.map(process)
    futures.toList.sequenceU
  }

  /**
   * Generic function retrieving the results of the action run in async futures
   *
   * @param futures Sequence of the futures containing the results to be retrieved
   * @param process Post-processing function to apply to the retrieved results
   * @return A sequence of results retrieved from the asynchronous actions performed in the futures
   */
  def retrieveFutures[J, T, R](futures: Seq[Future[(J, T)]])(process: ((J, T)) ⇒ R) = {
    val res = Future.gatherUnordered(futures)
    res.run.map(process)
  }

  def jobActions[E, J, R](processAction: J ⇒ Reader[E, Future[(J, ExecResult)]])(retrieveAction: ((J, ExecResult)) ⇒ (J, R))(jobs: J*)(implicit e: E) =
    SSHHost.withReusedConnection { connection ⇒
      val futures = processFutures(jobs: _*)(processAction) run (e)
      retrieveFutures(futures)(retrieveAction)
    }
}

object SSHHost {

  // FIXME maybe swap with non-reused in trait/object
  def withReusedConnection[T](f: SSHClient ⇒ T): Reader[SSHClient, T] = Reader(f)

  def withReusedSFTPClient[T](f: SFTPClient ⇒ T): Reader[SFTPClient, T] = Reader(f)

  def withSSH[T](f: ((SSHClient, SFTPClient)) ⇒ T) = Reader(f)
}
