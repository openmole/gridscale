/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package fr.iscpif.gridscale.tools

import ch.ethz.ssh2.Connection
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import scala.collection.mutable.HashMap
import fr.iscpif.gridscale.authentication._

object ConnectionCache {
  
  type SSHHost = {
    def user: String
    def host: String
    def port: Int
    def connect(implicit authentication: SSHAuthentication): Connection
  }
  
  lazy val default = new ConnectionCache {
    def connectionKeepAlive = 120 * 1000
  }
  
}

import ConnectionCache._

trait ConnectionCache { cache =>

  def connectionKeepAlive: Long
  
  class ConnectionInfo(val connection: Connection) {
    var lastConnectionUse: Long = System.currentTimeMillis
    var used = 0
    
    def get = {
      used += 1
      connection
    }
    
    def release = {
      used -= 1
      lastConnectionUse = System.currentTimeMillis
    }
    
    def recentlyUsed = 
      used > 0 || lastConnectionUse + connectionKeepAlive < System.currentTimeMillis 
    
  }
  
  private val connectionCloser = Executors.newSingleThreadScheduledExecutor(daemonThreadFactory)
  private val connections = new HashMap[(String, String, Int), ConnectionInfo]
  
  def adaptorKey(host: SSHHost) = (host.user, host.host, host.port)
  
  def cached(host: SSHHost)(implicit authentication: SSHAuthentication): Connection = synchronized {
    connections.getOrElseUpdate(adaptorKey(host), new ConnectionInfo(host.connect)).get
  }
  
  def release(host: SSHHost) = synchronized {
    val key = adaptorKey(host)
    connections(key).release
    connectionCloser.schedule(
        new Runnable {
          def run = cache.synchronized {
            connections.get(key) match {
              case Some(c) if(!c.recentlyUsed) => 
                c.connection.close
                connections.remove(key)
              case _ =>
            }
          }
        },
        (connectionKeepAlive * 1.05).toLong,
        TimeUnit.MILLISECONDS
      )
  }
  
}
