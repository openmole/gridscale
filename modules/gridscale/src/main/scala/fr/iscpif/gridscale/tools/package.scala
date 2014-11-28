/*
 * Copyright (C) 2014 Romain Reuillon
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

import java.io._
import java.util.concurrent._

import scala.concurrent.duration.Duration

package object tools {

  val daemonThreadFactory = new ThreadFactory {
    override def newThread(r: Runnable): Thread = {
      val t = new Thread(r)
      t.setDaemon(true)
      t
    }
  }

  val defaultExecutor = Executors.newCachedThreadPool(daemonThreadFactory)

  def timeout[F](f: ⇒ F)(timeout: Duration)(implicit executor: ExecutorService = defaultExecutor): F = {
    val r = executor.submit(new Callable[F] { def call = f })
    try r.get(timeout.length, timeout.unit)
    catch {
      case e: TimeoutException ⇒ r.cancel(true); throw e
    }
  }

  def copy(from: File, to: OutputStream, buffSize: Int, timeout: Duration) = {
    val inputStream = new BufferedInputStream(new FileInputStream(from))

    try Iterator.continually {
      val b = Array.ofDim[Byte](buffSize)
      val r = inputStream.read(b, 0, buffSize)
      (b, r)
    }.takeWhile { case (_, r) ⇒ r != -1 }.foreach {
      case (b, r) ⇒ this.timeout(to.write(b, 0, r))(timeout)
    } finally inputStream.close
  }

  def copy(from: InputStream, to: File, buffSize: Int, timeout: Duration) = {
    val outputStream = new BufferedOutputStream(new FileOutputStream(to))

    try Iterator.continually {
      val b = Array.ofDim[Byte](buffSize)
      val r = this.timeout(from.read(b, 0, buffSize))(timeout)
      (b, r)
    }.takeWhile { case (_, r) ⇒ r != -1 }.foreach {
      case (b, r) ⇒ outputStream.write(b, 0, r)
    } finally outputStream.close
  }

  def getBytes(from: InputStream, buffSize: Int, timeout: Duration) = {
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

  private val COPY_BUFFER_SIZE = 8192

  private type Readable[T] = {
    def close(): Unit
    def read(b: Array[T]): Int
  }

  def copyStream(is: InputStream, os: OutputStream) = {
    val buffer = new Array[Byte](COPY_BUFFER_SIZE)
    Iterator continually (is read buffer) takeWhile (_ != -1) filter (_ > 0) foreach { read ⇒
      os.write(buffer, 0, read)
    }
  }

  implicit class DurationDecorator(d: Duration) {
    def toHHmmss = {
      val millis = d.toMillis

      f"${millis / (1000 * 60 * 60)}%02d:${(millis % (1000 * 60 * 60)) / (1000 * 60)}%02d:${((millis % (1000 * 60 * 60)) % (1000 * 60)) / 1000}%02d"
    }
  }
}
