/*
* Copyright (C) 2012 Romain Reuillon
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

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import scala.collection.mutable.HashMap
import net.schmizz.sshj.SSHClient
import java.util.concurrent.atomic.AtomicInteger
import fr.iscpif.gridscale._

object ConnectionCache {

  type SSHHost = {
    def user: String
    def host: String
    def port: Int
    def connect(implicit authentication: SSHAuthentication): SSHClient
  }

  lazy val default = new ConnectionCache {
    def connectionKeepAlive = 120 * 1000
  }

  class ConnectionInfo(val connection: SSHClient) {
    @volatile var lastConnectionUse: Long = System.currentTimeMillis
    var used = new AtomicInteger()

    def get = {
      lastConnectionUse = System.currentTimeMillis
      used.incrementAndGet
      connection
    }

    def release = {
      used.getAndDecrement
      lastConnectionUse = System.currentTimeMillis
    }

    def recentlyUsed(connectionKeepAlive: Long) =
      used.get > 0 || (lastConnectionUse + connectionKeepAlive < System.currentTimeMillis)

    def working = (connection.isConnected && connection.isAuthenticated)

  }
}

import ConnectionCache._

trait ConnectionCache { cache ⇒

  def connectionKeepAlive: Long

  private val connectionCloser = Executors.newSingleThreadScheduledExecutor(daemonThreadFactory)
  private val connections = new HashMap[(String, String, Int), ConnectionInfo]

  def adaptorKey(host: SSHHost) = (host.user, host.host, host.port)

  def cached(host: SSHHost)(implicit authentication: SSHAuthentication) = synchronized {
    clean
    connections.getOrElseUpdate(adaptorKey(host), new ConnectionInfo(host.connect))
  }

  def release(info: ConnectionInfo) = synchronized {
    info.release
    connectionCloser.schedule(
      new Runnable {
        def run =
          cache.synchronized {
            if (!info.recentlyUsed(connectionKeepAlive)) info.connection.close
            clean
          }
      },
      (connectionKeepAlive * 1.05).toLong,
      TimeUnit.MILLISECONDS)
  }

  def clean = synchronized {
    val nonWorking = connections.toList.filterNot { case (_, v) ⇒ v.working }.unzip._1
    nonWorking.foreach(connections.remove)
  }

}
