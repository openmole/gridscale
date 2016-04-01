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

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global

case class Reader[E, A](run: E ⇒ A) {
  def map[B](f: A ⇒ B): Reader[E, B] = Reader(env ⇒ f(run(env)))
  def flatMap[B](f: A ⇒ Reader[E, B]): Reader[E, B] = Reader(env ⇒ f(run(env)).run(env))
}

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
  def processFutures[J, R](jobs: J*)(process: J ⇒ Reader[SSHClient, Future[(J, R)]]) = SSHHost.withReusedConnection {
    connection ⇒
      val futures = jobs.map(process).map(_.run(connection))
      futures
  }

  /**
   * Generic function retrieving the results of the action run in async futures
   *
   * @param futures Sequence of the futures containing the results to be retrieved
   * @param process Post-processing function to apply to the retrived results
   * @return A sequence of results retrieved from the asynchronous actions performed in the futures
   */
  def retrieveFutures[J, T, R](futures: Seq[Future[(J, T)]])(process: ((J, T)) ⇒ R) = {
    val res = Await.result(
      Future.sequence(futures),
      Duration.Inf)
    res.map(process)
  }

  def jobActions[J, R](processAction: J ⇒ Reader[SSHClient, Future[(J, ExecResult)]])(retrieveAction: ((J, ExecResult)) ⇒ (J, R))(jobs: J*) =
    SSHHost.withReusedConnection { connection ⇒
      val futures = processFutures(jobs: _*)(processAction).run(connection)
      retrieveFutures(futures)(retrieveAction)
    }
}

object SSHHost {
  def withReusedConnection[T](f: SSHClient ⇒ T): Reader[SSHClient, T] = Reader(f)
}
