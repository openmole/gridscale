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
package fr.iscpif.gridscale.egi

import java.io.{ InputStream, OutputStream, PipedInputStream, PipedOutputStream }
import com.github.sardine.impl._
import fr.iscpif.gridscale.egi.https._
import fr.iscpif.gridscale.storage._
import org.apache.http.client.methods.{ HttpDelete, HttpPut, HttpUriRequest }
import org.apache.http.conn.socket.ConnectionSocketFactory
import org.apache.http.protocol.HttpContext
import org.apache.http.{ HttpRequest, HttpResponse }

import scala.collection.JavaConversions._
import scala.concurrent.duration._

object EGIWebdav {
  def apply[A: HTTPSAuthentication](service: String, basePath: String, connections: Int = 20, timeout: Duration = 1 minute)(authentication: A) = {
    val (_service, _basePath, _connections, _timeout) = (service, basePath, connections, timeout)
    new EGIWebdav {
      override def basePath: String = _basePath
      override def factory: (Duration) ⇒ ConnectionSocketFactory = implicitly[HTTPSAuthentication[A]].factory(authentication)
      override def service: String = _service
      override def timeout = _timeout
      override def maxConnections = _connections
    }
  }

  class RedirectStrategy extends SardineRedirectStrategy {
    override def isRedirectable(method: String): Boolean =
      if (method.equalsIgnoreCase(HttpPut.METHOD_NAME)) true else super.isRedirectable(method)

    override def getRedirect(request: HttpRequest, response: HttpResponse, context: HttpContext): HttpUriRequest = {
      val method = request.getRequestLine().getMethod()
      val uri = getLocationURI(request, response, context)
      if (method.equalsIgnoreCase(HttpPut.METHOD_NAME)) new HttpPut(uri)
      else super.getRedirect(request, response, context)
    }
  }

}

trait EGIWebdav <: HTTPSClient with Storage {

  def service: String
  def basePath: String
  def timeout: Duration

  lazy val webdavClient = {
    val client = clientBuilder
    client.setRedirectStrategy(new EGIWebdav.RedirectStrategy)
    new SardineImpl(client)
  }

  def fullUrl(path: String) =
    trimSlashes(service) + "/" + trimSlashes(basePath) + "/" + trimSlashes(path)

  override protected def _openOutputStream(path: String): OutputStream = {
    val in = new PipedInputStream
    val out = new PipedOutputStream(in)
    webdavClient.put(fullUrl(path), in)
    out
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
  override def exists(path: String): Boolean = webdavClient.exists(fullUrl(path))
}
