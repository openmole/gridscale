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
import java.util.concurrent.{ TimeUnit, _ }
import java.util.concurrent.atomic.AtomicBoolean

import com.github.sardine.DavResource
import com.github.sardine.impl.SardineRedirectStrategy
import com.github.sardine.impl.handler.MultiStatusResponseHandler
import com.github.sardine.impl.methods.{ HttpMkCol, HttpMove, HttpPropFind }
import fr.iscpif.gridscale.storage._
import org.apache.http._
import org.apache.http.client.methods._
import org.apache.http.entity.InputStreamEntity
import org.apache.http.protocol.{ HttpContext, HTTP }

import scala.annotation.tailrec
import scala.collection.JavaConversions._
import scala.concurrent.duration._
import scala.util.{ Failure, Success, Try }

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

  def redirectStrategy = new SardineRedirectStrategy {
    override def getRedirect(request: HttpRequest, response: HttpResponse, context: HttpContext): HttpUriRequest = {
      val method = request.getRequestLine().getMethod()
      if (method.equalsIgnoreCase(HttpPut.METHOD_NAME)) {
        val uri = getLocationURI(request, response, context)
        RequestBuilder.put(uri).setEntity(request.asInstanceOf[HttpEntityEnclosingRequest].getEntity).build()
      } else super.getRedirect(request, response, context)
    }
    override protected def isRedirectable(method: String) =
      if (method.equalsIgnoreCase(HttpPut.METHOD_NAME)) true
      else super.isRedirectable(method)
  }

}

trait DPMWebDAVStorage <: HTTPSClient with Storage { dav ⇒

  def location: WebDAVLocation
  def timeout: Duration

  @transient lazy val httpClient = newClient.setRedirectStrategy(DPMWebDAVStorage.redirectStrategy).build()

  override def finalize = {
    super.finalize()
    httpClient.close()
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
    val pipe = new Pipe(timeout, path)

    val future =
      executor.submit(
        new Runnable {
          def run =
            try {
              val put = new HttpPut(fullUrl(path))
              val entity = new InputStreamEntity(pipe.is, -1)
              put.setEntity(entity)
              put.addHeader(HTTP.EXPECT_DIRECTIVE, HTTP.EXPECT_CONTINUE)
              val r = httpClient.execute(put)
              testAndClose(r)
            } catch {
              case t: Throwable ⇒
                Try(_rmFile(path))
                throw new IOException(s"Error putting output stream for $path on $dav", t)
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

  override def _makeDir(path: String): Unit = {
    val mkcol = new HttpMkCol(fullUrl(path))
    testAndClose(httpClient.execute(mkcol))
  }

  override def _mv(from: String, to: String): Unit = {
    val move = new HttpMove(fullUrl(from), fullUrl(to), true)
    testAndClose(httpClient.execute(move))
  }

  override def _rmDir(path: String): Unit = {
    val delete = new HttpDelete(fullUrl(path))
    delete.addHeader("Depth", "infinity")
    testAndClose(httpClient.execute(delete))
  }

  def listProp(path: String)=  {
      val entity = new HttpPropFind(fullUrl(path))
      entity.setDepth(1.toString)
      val multistatus = httpClient.execute(entity, new MultiStatusResponseHandler)
      val responses = multistatus.getResponse
      responses.map(new DavResource(_))
   }


  override def _list(path: String): Seq[ListEntry] = {
    for { r ← listProp(path).drop(1) } yield {
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

  def isResponseOk(response: HttpResponse) =
    response.getStatusLine.getStatusCode >= HttpStatus.SC_OK &&
      response.getStatusLine.getStatusCode < HttpStatus.SC_MULTIPLE_CHOICES

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

  def testAndClose(r: CloseableHttpResponse) =
    try testResponse(r).get
    finally r.close

  def testResponse(response: HttpResponse) = Try {
    if (!isResponseOk(response)) throw new IOException(s"Server responded with an error: ${response.getStatusLine.getStatusCode} ${response.getStatusLine.getReasonPhrase}")
  }

  override def _rmFile(path: String): Unit = {
    val delete = new HttpDelete(fullUrl(path))
    testAndClose(httpClient.execute(delete))
  }

  override def _exists(path: String): Boolean = {
    val head = new HttpHead(fullUrl(path))
    val response = httpClient.execute(head)

    try {
      response.getStatusLine.getStatusCode match {
        case x if x < HttpStatus.SC_MULTIPLE_CHOICES ⇒ true
        case HttpStatus.SC_NOT_FOUND                 ⇒ false
        case _                                       ⇒ throw new IOException(s"Server responded with an unexpected response: ${response.getStatusLine.getStatusCode} ${response.getStatusLine.getReasonPhrase}")
      }
    } finally response.close()

  }

  override def toString = fullUrl("")

  class Pipe(timeout: Duration, path: String) {
    var reader = (None: Option[Future[_]])

    val writerClosed = new AtomicBoolean(false)
    val readerClosed = new AtomicBoolean(false)

    val buffer = new RingBuffer[Int](1024 * 64)

    def future = reader.get

    val is = new InputStream {

      override def read(): Int = {
        @tailrec def waitRead(): Int =
          if (writerClosed.get()) -1
          else {
            buffer.tryDequeue() match {
              case None ⇒
                buffer.waitNotEmpty
                waitRead()
              case Some(r) ⇒ (r & 0xFF)
            }
          }

        val res = waitRead()
        res
      }

      override def close() = {
        readerClosed.set(true)
        buffer.synchronized { buffer.notifyAll() }
      }

    }

    val os = new OutputStream {

      var size = 0

      override def write(i: Int): Unit = {
        @tailrec def waitWrite(): Unit = {
          if (!readerClosed.get()) {
            if (!buffer.tryEnqueue(i)) {
              buffer.waitNotFull
              waitWrite()
            }
          }
        }

        size += 1
        waitWrite()
      }

      override def close(): Unit = {
        @tailrec def waitClose(): Unit = {
          if (!readerClosed.get() && !buffer.isEmpty) {
            buffer.waitEmpty
            waitClose()
          }
        }

        try waitClose()
        finally {
          writerClosed.set(true)
          buffer.synchronized { buffer.notifyAll() }
        }

        try future.get(timeout.toMillis, TimeUnit.MILLISECONDS)
        catch {
          case e: TimeoutException ⇒
            future.cancel(true)
            throw e
          case e: Throwable ⇒ throw e
        }

        val serverSize: Long = listProp(path).head.getContentLength
        if(size.toLong != serverSize) {
          Try(_rmFile(path))
          throw new IOException(s"Written file has a wrong size on the remote server")
        }
      }
    }
  }
}
