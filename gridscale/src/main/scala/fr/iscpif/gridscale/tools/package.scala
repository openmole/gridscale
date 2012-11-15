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

package fr.iscpif.gridscale

import com.sun.xml.internal.messaging.saaj.util.ByteOutputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeoutException
import java.util.concurrent.TimeUnit

package object tools {

  val daemonThreadFactory = new ThreadFactory {
    override def newThread(r: Runnable): Thread = {
      val t = new Thread(r)
      t.setDaemon(true)
      t
    }
  }

  val defaultExecutor = Executors.newCachedThreadPool(daemonThreadFactory)

  def timeout[F](f: ⇒ F)(duration: Long)(implicit executor: ExecutorService = defaultExecutor): F = {
    val r = executor.submit(new Callable[F] { def call = f })
    try r.get(duration, TimeUnit.SECONDS)
    catch {
      case e: TimeoutException ⇒ r.cancel(true); throw e
    }
  }

  def copy(from: File, to: OutputStream, buffSize: Int, timeout: Long) = {
    val inputStream = new BufferedInputStream(new FileInputStream(from))

    try Iterator.continually {
      val b = Array.ofDim[Byte](buffSize)
      val r = inputStream.read(b, 0, buffSize)
      (b, r)
    }.takeWhile { case (_, r) ⇒ r != -1 }.foreach {
      case (b, r) ⇒ this.timeout(to.write(b, 0, r))(timeout)
    } finally inputStream.close
  }

  def copy(from: InputStream, to: File, buffSize: Int, timeout: Long) = {
    val outputStream = new BufferedOutputStream(new FileOutputStream(to))

    try Iterator.continually {
      val b = Array.ofDim[Byte](buffSize)
      val r = this.timeout(from.read(b, 0, buffSize))(timeout)
      (b, r)
    }.takeWhile { case (_, r) ⇒ r != -1 }.foreach {
      case (b, r) ⇒ outputStream.write(b, 0, r)
    } finally outputStream.close
  }

  def getBytes(from: InputStream, buffSize: Int, timeout: Long) = {
    val os = new ByteArrayOutputStream
    Iterator.continually {
      val b = Array.ofDim[Byte](buffSize)
      val r = this.timeout(from.read(b, 0, buffSize))(timeout)
      (b, r)
    }.takeWhile { case (_, r) ⇒ r != -1 }.foreach {
      case (b, r) ⇒ os.write(b, 0, r)
    }
    os.toByteArray
  }

}