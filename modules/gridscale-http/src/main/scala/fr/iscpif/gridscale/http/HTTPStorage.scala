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

package fr.iscpif.gridscale.http

import java.io.{ IOException, File, InputStream, OutputStream }
import java.net.{ HttpURLConnection, URI }

import com.github.sardine.impl.methods.HttpPropFind
import fr.iscpif.gridscale._
import fr.iscpif.gridscale.storage._
import fr.iscpif.gridscale.tools.{ _ }
import org.apache.http._
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods._
import org.apache.http.config.SocketConfig
import org.apache.http.impl.client._
import org.apache.http.impl.conn.BasicHttpClientConnectionManager
import org.apache.http.protocol._
import org.htmlparser.Parser
import org.htmlparser.filters.NodeClassFilter
import org.htmlparser.tags.LinkTag

import scala.concurrent.duration._
import scala.util.{ Success, Failure, Try }

object HTTPStorage {

  def redirectStrategy = new LaxRedirectStrategy {
    override def getRedirect(request: HttpRequest, response: HttpResponse, context: HttpContext): HttpUriRequest = {
      assert(response.getStatusLine.getStatusCode < HttpStatus.SC_BAD_REQUEST, "Error while redirecting request")
      super.getRedirect(request, response, context)
    }

    override protected def isRedirectable(method: String) =
      method match {
        case HttpPropFind.METHOD_NAME ⇒ true
        //  case HttpPut.METHOD_NAME      ⇒ true
        case _                        ⇒ super.isRedirectable(method)
      }
  }

  def connectionManager(timeout: Duration) = {
    val client = new BasicHttpClientConnectionManager()
    val socketConfig = SocketConfig.custom().setSoTimeout(timeout.toMillis.toInt).build()
    client.setSocketConfig(socketConfig)
    client
  }

  def requestConfig(timeout: Duration) =
    RequestConfig.custom()
      .setSocketTimeout(timeout.toMillis.toInt)
      .setConnectTimeout(timeout.toMillis.toInt)
      .setConnectionRequestTimeout(timeout.toMillis.toInt)
      .build()

  def newClient(timeout: Duration) =
    HttpClients.custom().
      setRedirectStrategy(redirectStrategy).
      setConnectionManager(connectionManager(timeout)).
      setDefaultRequestConfig(requestConfig(timeout)).build()

  def withConnection[T](uri: URI, timeout: Duration)(f: HttpURLConnection ⇒ T): T = {
    val relativeURL = uri.toURL
    val cnx = relativeURL.openConnection.asInstanceOf[HttpURLConnection]
    cnx.setConnectTimeout(timeout.toMillis.toInt)
    cnx.setReadTimeout(timeout.toMillis.toInt)
    if (cnx.getHeaderField(null) == null) throw new RuntimeException("Failed to connect to url: " + relativeURL + " response code was " + cnx.getResponseCode)
    else f(cnx)
  }

  def isResponseOk(response: HttpResponse) =
    response.getStatusLine.getStatusCode >= HttpStatus.SC_OK &&
      response.getStatusLine.getStatusCode < HttpStatus.SC_BAD_REQUEST

  def toInputStream(uri: URI, timeout: Duration = 1 minute): InputStream = toInputStream(uri, newClient(timeout))

  def toInputStream(uri: URI, httpClient: CloseableHttpClient): InputStream = {
    val get = new HttpGet(uri)
    get.addHeader(HTTP.EXPECT_DIRECTIVE, HTTP.EXPECT_CONTINUE)
    val response = httpClient.execute(get)

    HTTPStorage.testResponse(response) match {
      case Failure(e) ⇒
        get.releaseConnection()
        response.close()
        throw e
      case Success(_) ⇒
    }

    val stream = response.getEntity.getContent

    new InputStream {
      override def read(): Int = stream.read()
      override def close() = {
        get.releaseConnection()
        response.close()
        httpClient.close()
      }
    }
  }

  def apply(url: String, timeout: Duration = 1 minute) = {
    val (_url, _timeout) = (url, timeout)
    new HTTPStorage {
      override val url: String = _url
      override val timeout = _timeout
    }
  }

  def execute(execute: HttpRequestBase ⇒ CloseableHttpResponse, request: HttpRequestBase) =
    try {
      val r = execute(request)
      val returnCode = r.getStatusLine.getStatusCode
      try testResponse(r).get
      finally r.close
      returnCode
    } finally request.releaseConnection()

  def testResponse(response: HttpResponse) = Try {
    if (!isResponseOk(response)) throw new IOException(s"Server responded with an error: ${response.getStatusLine.getStatusCode} ${response.getStatusLine.getReasonPhrase}")
  }

  def parseHTMLListing(page: String) = {
    val parser = new Parser
    parser.setInputHTML(page)
    val list = parser.extractAllNodesThatMatch(new NodeClassFilter(classOf[LinkTag]))

    list.toNodeArray.flatMap {
      l ⇒
        val entryName = l.getText.substring("a href=\"".size, l.getText.size - 1)
        val isDir = entryName.endsWith("/")
        val name = if (isDir) entryName.substring(0, entryName.length - 1) else entryName
        if (!name.isEmpty && !name.contains("/") && !name.contains("?") && !name.contains("#")) {
          val ret = name.replaceAll("&amp;", "%26")
          Some(
            ListEntry(
              new File(java.net.URLDecoder.decode(ret, "utf-8")).getPath,
              if (isDir) DirectoryType else FileType,
              None
            )
          )
        } else None
    }
  }

}

trait HTTPStorage extends Storage {

  def url: String
  def bufferSize = 64 * 1024
  def timeout: Duration

  def _list(path: String) = {
    val is = _read(path)
    try HTTPStorage.parseHTMLListing(new String(getBytes(is, bufferSize, timeout)))
    finally is.close
  }

  def _makeDir(path: String) =
    throw new RuntimeException("Operation not supported for http protocol")

  def _rmDir(path: String) =
    throw new RuntimeException("Operation not supported for http protocol")

  def _rmFile(patg: String) =
    throw new RuntimeException("Operation not supported for http protocol")

  def _mv(from: String, to: String) =
    throw new RuntimeException("Operation not supported for http protocol")

  def _read(path: String): InputStream =
    HTTPStorage.toInputStream(new URI(url + "/" + path), HTTPStorage.newClient(timeout))

  def _write(is: InputStream, path: String) =
    throw new RuntimeException("Operation not supported for http protocol")

}
