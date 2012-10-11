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

package fr.iscpif.gridscale.storage

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import fr.iscpif.gridscale.tools._
import org.htmlparser.Parser
import collection.JavaConversions._
import org.htmlparser.filters.NodeClassFilter
import org.htmlparser.tags.LinkTag

trait HTTPStorage extends Storage {

  type A = Unit
  def url: String
  
  def timeout = 120
  def bufferSize = 64000
  
  def list(path: String)(implicit authentication: A): Seq[(String, FileType)] = {
    val is = openInputStream(path)(authentication)
    try {     
      val parser = new Parser
      parser.setInputHTML(new String(getBytes(is, 64000, timeout)))
      val  list =  parser.extractAllNodesThatMatch(new NodeClassFilter (classOf[LinkTag]))
  
      list.toNodeArray.flatMap {
        l => 
        val entryName = l.getText.substring("a href=\"".size, l.getText.size - 1)
        val isDir = entryName.endsWith("/")
        val name = if(isDir) entryName.substring(0, entryName.length - 1) else entryName
        if(!name.isEmpty && !name.contains("/") && !name.contains("?") && !name.contains("#")) {
          val ret = name.replaceAll("&amp;","%26")
          Some(
            (new File(java.net.URLDecoder.decode(ret, "utf-8")).getPath, if(isDir) DirectoryType else FileType)
          )
        } else None
      }
    } finally is.close
  }
  
  def makeDir(path: String)(implicit authentication: A) = 
    throw new RuntimeException("Operation not supported for http protocol")
  
  def rmDir(path: String)(implicit authentication: A) = 
    throw new RuntimeException("Operation not supported for http protocol")
  
  def rmFile(patg: String)(implicit authentication: A) = 
    throw new RuntimeException("Operation not supported for http protocol")
  
  protected def _openInputStream(path: String)(implicit authentication: A): InputStream = withConnection(path) {
    _.getInputStream
  }

  protected def _openOutputStream(path: String)(implicit authentication: A): OutputStream =
    throw new RuntimeException("Operation not supported for http protocol")
  
  
  private def withConnection[T](path: String)(f: HttpURLConnection => T): T = {
    val relativeURL =  new URI(url + "/" + path).toURL
    val cnx = relativeURL.openConnection.asInstanceOf[HttpURLConnection]
    cnx.setConnectTimeout(timeout * 1000)
    cnx.setReadTimeout(timeout * 1000)
    if(cnx.getHeaderField(null) == null) throw new RuntimeException("Failed to connect to url: "+ relativeURL)
    else f(cnx)
  }
  
}
