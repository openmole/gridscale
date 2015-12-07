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
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent._
import com.github.sardine.DavResource
import com.github.sardine.impl._
import com.github.sardine.impl.handler.MultiStatusResponseHandler
import com.github.sardine.impl.methods.HttpPropFind
import com.github.sardine.model.Multistatus
import fr.iscpif.gridscale.storage._
import org.apache.http
import org.apache.http._
import org.apache.http.client.methods._
import org.apache.http.entity.InputStreamEntity
import org.apache.http.protocol.{ HTTP }

import fr.iscpif.gridscale.tools._
import scala.annotation.tailrec
import scala.collection.JavaConversions._
import scala.concurrent.duration._
import scala.util.{ Success, Failure, Try }
import scala.io.Source

case class WebDAVLocation(host: String, basePath: String, port: Int = 443)

object DPMWebDAVStorage {
  def apply[A: HTTPSAuthentication](location: WebDAVLocation, connections: Option[Int] = Some(20), timeout: Duration = 1 minute)(authentication: A) = {
    val (_location, _connections, _timeout) = (location, connections, timeout)
    new DPMWebDAVStorage {
      override def location = _location
      override def factory = implicitly[HTTPSAuthentication[A]].factory(authentication)
      override def timeout = _timeout
      override def pool = _connections
    }
  }

  def redirectStrategy = new SardineRedirectStrategy
  /*{
    override def getRedirect(request: HttpRequest, response: HttpResponse, context: HttpContext): HttpUriRequest = {
      val method = request.getRequestLine().getMethod()
      if (method.equalsIgnoreCase(HttpPut.METHOD_NAME)) {
        val uri = getLocationURI(request, response, context)
        RequestBuilder.put(uri).setEntity(request.asInstanceOf[HttpEntityEnclosingRequest].getEntity).build()
      } else super.getRedirect(request, response, context)
    }
  }*/

  class Pipe(timeout: Duration) {

    var reader = (None: Option[Future[_]])

    @volatile var writerClosed = false
    @volatile var readerClosed = false

    val buffer = new RingBuffer[Int](1024 * 64)

    def future = reader.get

    val is = new InputStream {
      override def read(): Int = {
        @tailrec def waitRead(): Int =
          if (writerClosed) -1
          else {
            buffer.tryDequeue() match {
              case None ⇒
                buffer.waitNotEmpty
                waitRead()
              case Some(r) ⇒ (r & 0xFF)
            }
          }

        waitRead()
      }

      override def close() = {
        readerClosed = true
        buffer.synchronized { buffer.notifyAll() }
      }

    }

    val os = new OutputStream {
      override def write(i: Int): Unit = {
        @tailrec def waitWrite(): Unit = {
          if (!readerClosed) {
            if (!buffer.tryEnqueue(i)) {
              buffer.waitNotFull
              waitWrite()
            }
          }
        }

        waitWrite()
      }

      override def close(): Unit = {
        @tailrec def waitClose(): Unit = {
          if (!readerClosed && !buffer.isEmpty) {
            buffer.waitEmpty
            waitClose()
          }
        }

        try waitClose()
        finally {
          writerClosed = true
          buffer.synchronized { buffer.notifyAll() }
        }

        try future.get(timeout.toMillis, TimeUnit.MILLISECONDS)
        catch {
          case e: TimeoutException ⇒
            future.cancel(true)
            throw e
          case e: Throwable ⇒ throw e
        }
      }
    }
  }

}

trait DPMWebDAVStorage <: HTTPSClient with Storage with RecursiveRmDir { dav ⇒

  def location: WebDAVLocation
  def timeout: Duration

  @transient lazy val client = {
    val c = newClient
    c.setRedirectStrategy(DPMWebDAVStorage.redirectStrategy)
    c
  }

  @transient lazy val httpClient = client.build()
  @transient lazy val webdavClient = new SardineImpl(client)

  override def finalize = {
    super.finalize()
    httpClient.close()
    webdavClient.shutdown()
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

    import DPMWebDAVStorage._
    val pipe = new Pipe(timeout)

    val future =
      executor.submit(
        new Runnable {
          def run = try {
            // webdavClient.put(fullUrl(path), pipe.is)
            def buildPut(uri: URI) = {
              val put = new HttpPut(uri)
              val entity = new InputStreamEntity(pipe.is, -1)
              put.setEntity(entity)
              put.addHeader(HTTP.EXPECT_DIRECTIVE, HTTP.EXPECT_CONTINUE)
              put
            }

            def execute(request: HttpPut): CloseableHttpResponse = {
              val response = httpClient.execute(request)
              getRedirection(response) match {
                case Some(uri) ⇒
                  response.close()
                  execute(buildPut(uri))
                case None ⇒
                  response
              }
            }

            val put = buildPut(new URI(fullUrl(path)))
            val response = execute(put)

            try testResponse(response).get
            finally response.close()
          } catch {
            case t: Throwable ⇒ throw new IOException(s"Error putting output stream for $path on $dav", t)
          } finally pipe.is.close()
        }
      )

    pipe.reader = Some(future)
    pipe.os
  }

  override protected def _openInputStream(path: String): InputStream = {
    val get = new HttpGet(fullUrl(path))
    get.addHeader(HTTP.EXPECT_DIRECTIVE, HTTP.EXPECT_CONTINUE)
    val response = httpClient.execute(get)

    testResponse(response) match {
      case Failure(e) ⇒
        response.close()
        throw e
      case Success(_) ⇒
    }

    val stream = response.getEntity.getContent

    new InputStream {
      override def read(): Int = stream.read()
      override def close() = response.close()
    }
  }

  override def _makeDir(path: String): Unit =
    webdavClient.createDirectory(fullUrl(path))

  override def _mv(from: String, to: String): Unit =
    webdavClient.move(fullUrl(from), fullUrl(to))

  override def rmEmptyDir(path: String): Unit = webdavClient.delete(fullUrl(path))

  override def _list(path: String): Seq[ListEntry] = {
    val entity = new HttpPropFind(fullUrl(path))
    entity.setDepth(1.toString)
    val multistatus = httpClient.execute(entity, new MultiStatusResponseHandler)
    val responses = multistatus.getResponse
    for { r ← responses.drop(1).map(new DavResource(_)) } yield {
      ListEntry(
        name = r.getName,
        `type` = if (r.isDirectory) DirectoryType else FileType,
        Some(r.getModified.getTime)
      )
    }
    //FIXME workaround when propfind doesn't work (in DPM)
    /*val request = new HttpGet(fullUrl(path))
    val response = httpClient.execute(request)
    try {
      val is = response.getEntity.getContent
      HTTPStorage.parseHTMLListing(new String(getBytes(is, 1024, timeout)))
    } finally response.close*/
  }

  def isResponseOk(response: HttpResponse) = response.getStatusLine.getStatusCode >= HttpStatus.SC_OK && response.getStatusLine.getStatusCode < HttpStatus.SC_MULTIPLE_CHOICES

  def getRedirection(response: HttpResponse): Option[URI] =
    if (isRedirection(response)) {
      val locationHeader: Header = response.getFirstHeader("location")
      if (locationHeader == null) throw new ProtocolException("Received redirect response " + response.getStatusLine + " but no location header")
      Some(new URI(locationHeader.getValue))
    } else None

  def isRedirection(response: HttpResponse) =
    response.getStatusLine.getStatusCode match {
      case HttpStatus.SC_MOVED_TEMPORARILY |
        HttpStatus.SC_MOVED_PERMANENTLY |
        HttpStatus.SC_TEMPORARY_REDIRECT |
        HttpStatus.SC_SEE_OTHER ⇒ true
      case _ ⇒ false
    }

  def testResponse(response: HttpResponse) = Try {
    if (!isResponseOk(response)) throw new IOException(s"Server responded with an error: ${response.getStatusLine.getStatusCode} ${response.getStatusLine.getReasonPhrase}")
  }

  override def _rmFile(path: String): Unit = webdavClient.delete(fullUrl(path))
  override def _exists(path: String): Boolean = webdavClient.exists(fullUrl(path))

  override def toString = fullUrl("")
}
