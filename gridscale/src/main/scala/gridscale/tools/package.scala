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

package gridscale.tools

import java.io._
import java.util.concurrent._
import squants._

import scala.annotation.tailrec
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}


val daemonThreadFactory: ThreadFactory = new ThreadFactory {
  override def newThread(r: Runnable): Thread = {
    val t = new Thread(r)
    t.setDaemon(true)
    t
  }
}

val defaultExecutor: ExecutorService = Executors.newCachedThreadPool(daemonThreadFactory)

def timeout[F](f: ⇒ F)(timeout: Time)(implicit executor: ExecutorService = defaultExecutor): F = {
  val r = executor.submit(new Callable[F] { def call = f })
  try r.get(timeout.millis, TimeUnit.MILLISECONDS)
  catch {
    case e: TimeoutException ⇒ r.cancel(true); throw e
  }
}

def copy(from: File, to: OutputStream, buffSize: Int, timeout: Time): Unit = {
  val inputStream = new BufferedInputStream(new FileInputStream(from))

  try Iterator.continually {
    val b = Array.ofDim[Byte](buffSize)
    val r = inputStream.read(b, 0, buffSize)
    (b, r)
  }.takeWhile { case (_, r) ⇒ r != -1 }.foreach {
    case (b, r) ⇒ this.timeout(to.write(b, 0, r))(timeout)
  } finally inputStream.close
}

def copy(from: InputStream, to: File, buffSize: Int, timeout: Time): Unit = {
  val outputStream = new BufferedOutputStream(new FileOutputStream(to))

  try Iterator.continually {
    val b = Array.ofDim[Byte](buffSize)
    val r = this.timeout(from.read(b, 0, buffSize))(timeout)
    (b, r)
  }.takeWhile { case (_, r) ⇒ r != -1 }.foreach {
    case (b, r) ⇒ outputStream.write(b, 0, r)
  } finally outputStream.close
}

def getBytes(from: InputStream, buffSize: Int): Array[Byte] = {
  val os = new ByteArrayOutputStream
  Iterator.continually {
    val b = Array.ofDim[Byte](buffSize)
    val r = from.read(b, 0, buffSize)
    (b, r)
  }.takeWhile { case (_, r) ⇒ r != -1 }.foreach {
    case (b, r) ⇒ os.write(b, 0, r)
  }
  os.toByteArray
}

def getBytes(from: InputStream, buffSize: Int, timeout: Time): Array[Byte] = {
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

def copyStream(is: InputStream, os: OutputStream): Unit = {
  val buffer = new Array[Byte](COPY_BUFFER_SIZE)
  Iterator continually (is read buffer) takeWhile (_ != -1) filter (_ > 0) foreach { read ⇒
    os.write(buffer, 0, read)
  }
}

implicit class TimeDecorator(d: Time) {
  def toHHmmss: String = {
    val millis = d.millis
    f"${millis / (1000 * 60 * 60)}%02d:${(millis % (1000 * 60 * 60)) / (1000 * 60)}%02d:${((millis % (1000 * 60 * 60)) % (1000 * 60)) / 1000}%02d"
  }
}

/**
 * Extra operations for squants Information
 * @param info Memory value as squants Information
 */
implicit class InformationDecorator(info: squants.information.Information) {
  /**
   * Make sure memory requirements come up as long integer strings
   * Round up values if needed.
   * Force negatives to 0.
   * @return String containing the memory Information as MegaBytes
   */
  def toMBString: String = info.toMegabytes.max(0).round.toString
}

def findWorking[S, T](servers: Seq[S], f: S ⇒ T): T = {
  @tailrec
  def findWorking0(servers: List[S]): Try[T] =
    servers match {
      case Nil      ⇒ Failure(new RuntimeException("Server list is empty"))
      case h :: Nil ⇒ Try(f(h))
      case h :: tail ⇒
        Try(f(h)) match {
          case Failure(_) ⇒ findWorking0(tail)
          case s          ⇒ s
        }
    }

  findWorking0(servers.toList) match {
    case Failure(t) ⇒ throw new RuntimeException(s"No server is working among $servers", t)
    case Success(t) ⇒ t
  }
}


object ManifestCompat {
  implicit def classTagToManifest[T](implicit classTag: ClassTag[T]): Manifest[T] = new Manifest[T] {
    override def runtimeClass: Class[_] = classTag.runtimeClass
  }
}

def downloadFromStream(file: File)(is: java.io.InputStream) =
  val os = new BufferedOutputStream(new FileOutputStream(file))
  try
    Iterator.continually(is.read()).takeWhile(_ != -1).foreach: b =>
      os.write(b)
  finally os.close()

//  implicit class EitherDecorator[A, B](e: Either[A, B]) {
//    def leftMap[C](f: A ⇒ C): Either[C, B] = e match {
//      case Left(v)  ⇒ Left(f(v))
//      case Right(v) ⇒ Right(v)
//  }
//    }


object Effect:
  def apply[T](effect: ⇒ T) = new Effect(effect)


class Effect[T](effect: ⇒ T):
  inline def apply() = effect

object Random:
  def apply(seed: Long): Effect[Random] = Effect(new Random(new util.Random(seed)))
  def apply(random: util.Random): Effect[Random] = Effect(new Random(random))

class Random(val random: util.Random):
  def nextDouble() = random.nextDouble()
  def nextInt(n: Int) = random.nextInt(n)
  def nextBoolean() = random.nextBoolean()
  def shuffle[A](s: Vector[A]) = random.shuffle(s)
  def use[T](f: util.Random ⇒ T) = f(random)


object FileSystem:
  def read(file: File): String = readStream(file)(is ⇒ scala.io.Source.fromInputStream(is).mkString)

  def list(p: File) = p.listFiles.toVector

  def readStream[T](file: File)(f: java.io.InputStream ⇒ T): T =
    import java.io._
    val is = new BufferedInputStream(new FileInputStream(file))
    try f(is)
    finally is.close()

  def writeStream[T](file: File)(f: java.io.OutputStream ⇒ T) =
    import java.io._
    val is = new BufferedOutputStream(new FileOutputStream(file))
    try f(is)
    finally is.close()


class Lazy[+T](t: => T) extends (() ⇒ T):
  lazy val value: T = t
  def apply(): T = value


extension (lock: java.util.concurrent.locks.Lock)
  def apply[T](block: ⇒ T): T =
    lock.lock()
    try block
    finally lock.unlock()