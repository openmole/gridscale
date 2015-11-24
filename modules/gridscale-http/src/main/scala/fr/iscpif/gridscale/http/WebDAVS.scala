/*
 * Copyright (C) 2015 Romain Reuillon
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
package fr.iscpif.gridscale.http

import java.io._
import java.util.concurrent.{ Executors, Future, ThreadFactory }
import com.github.sardine.impl._
import fr.iscpif.gridscale.storage._
import org.apache.http._
import org.apache.http.client.methods.{ HttpDelete, HttpPut, HttpUriRequest, RequestBuilder }
import org.apache.http.conn.socket.ConnectionSocketFactory
import org.apache.http.protocol.HttpContext

import scala.collection.JavaConversions._
import scala.concurrent.duration._
import scala.util.{ Success, Failure, Try }

case class WebDAVLocation(host: String, basePath: String, port: Int = 443)

object WebDAVS {
  def apply[A: HTTPSAuthentication](location: WebDAVLocation, connections: Int = 20, timeout: Duration = 1 minute)(authentication: A) = {
    val (_location, _connections, _timeout) = (location, connections, timeout)
    new WebDAVS {
      override def location = _location
      override def factory: (Duration) ⇒ ConnectionSocketFactory = implicitly[HTTPSAuthentication[A]].factory(authentication)
      override def timeout = _timeout
      override def maxConnections = _connections
    }
  }

  class RedirectStrategy extends SardineRedirectStrategy {
    override def isRedirectable(method: String): Boolean =
      if (method.equalsIgnoreCase(HttpPut.METHOD_NAME)) true else super.isRedirectable(method)

    override def getRedirect(request: HttpRequest, response: HttpResponse, context: HttpContext): HttpUriRequest = {
      val method = request.getRequestLine().getMethod()
      if (method.equalsIgnoreCase(HttpPut.METHOD_NAME)) {
        val uri = getLocationURI(request, response, context)
        RequestBuilder.put(uri).setEntity(request.asInstanceOf[HttpEntityEnclosingRequest].getEntity).build()
      } else super.getRedirect(request, response, context)
    }
  }

}

trait WebDAVS <: HTTPSClient with Storage { dav =>

  def location: WebDAVLocation
  def timeout: Duration

  lazy val webdavClient = {
    val client = clientBuilder
    client.setRedirectStrategy(new WebDAVS.RedirectStrategy)
    new SardineImpl(client)
  }

  def fullUrl(path: String) =
    "https://" + trimSlashes(location.host) + ":" + location.port + "/" + trimSlashes(location.basePath) + "/" + trimSlashes(path)

  override protected def _openOutputStream(path: String): OutputStream = {
    val executor = Executors.newSingleThreadExecutor(new ThreadFactory {
      override def newThread(runnable: Runnable): Thread = {
        val thread = new Thread(runnable)
        thread.setDaemon(true)
        thread
      }
    })

    @volatile var future: Future[_] = null

    val os = new PipedOutputStream() {
      override def close() = {
        super.close
        future.get
      }
    }

    val is = new PipedInputStream(os)

    future = executor.submit(
      new Runnable {
        def run =
          Try(webdavClient.put(fullUrl(path), is)) match {
            case Failure(t) ⇒ throw new IOException(s"Error putting output stream for $path on $dav", t)
            case Success(s) ⇒ s
          }
      }
    )
    os
  }

  override protected def _openInputStream(path: String): InputStream =
    webdavClient.get(fullUrl(path))

  override def _makeDir(path: String): Unit =
    webdavClient.createDirectory(fullUrl(path))

  override def _mv(from: String, to: String): Unit =
    webdavClient.move(fullUrl(from), fullUrl(to))

  override def _rmDir(path: String): Unit = {
    val delete = new HttpDelete(fullUrl(path))
    webdavClient.delete(fullUrl(path))
  }

  override def _list(path: String): Seq[ListEntry] = {
    webdavClient.list(fullUrl(path)).map { e ⇒
      val t = if (e.isDirectory) DirectoryType else FileType
      ListEntry(e.getName, t, Some(e.getModified.getTime))
    }
  }

  override def _rmFile(path: String): Unit = webdavClient.delete(fullUrl(path))
  override def _exists(path: String): Boolean = webdavClient.exists(fullUrl(path))

  override def toString = fullUrl("")
}
