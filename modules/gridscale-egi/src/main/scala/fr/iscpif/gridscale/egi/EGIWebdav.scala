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

import java.io.{ InputStream, OutputStream }

import com.github.sardine.impl.SardineImpl
import fr.iscpif.gridscale.egi.https._
import fr.iscpif.gridscale.storage._
import fr.iscpif.gridscale.tools.DefaultTimeout
import org.apache.http.client.methods.HttpDelete
import org.apache.http.conn.socket.ConnectionSocketFactory

import scala.collection.JavaConversions._
import scala.concurrent.duration.Duration

object EGIWebdav {
  def apply[A: HTTPSAuthentication](service: String, basePath: String)(authentication: A) = {
    val (_service, _basePath) = (service, basePath)
    new EGIWebdav {
      override def basePath: String = _basePath
      override def factory: (Duration) ⇒ ConnectionSocketFactory = implicitly[HTTPSAuthentication[A]].factory(authentication)
      override def service: String = _service
    }
  }
}

trait EGIWebdav <: DefaultTimeout with HTTPSClient with Storage {

  def basePath: String

  lazy val webdavClient = new SardineImpl(clientBuilder)

  def fullUrl(path: String) =
    trimSlashes(service) + "/" + trimSlashes(basePath) + "/" + trimSlashes(path)

  override protected def _openOutputStream(path: String): OutputStream = ???

  override def _makeDir(path: String): Unit =
    webdavClient.createDirectory(fullUrl(path))

  override protected def _openInputStream(path: String): InputStream = ???

  override def _mv(from: String, to: String): Unit = ???

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

  override def _rmFile(path: String): Unit = ???

}
