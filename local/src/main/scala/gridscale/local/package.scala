package gridscale

import java.io._
import java.nio.file.{ FileAlreadyExistsException, FileSystemException, Path ⇒ JPath, StandardCopyOption, Files ⇒ JFiles }
import java.util.logging.Logger
import gridscale.tools.shell.BashShell

package object local {

  import effectaside._
  import gridscale.tools._
  import scala.io.Source
  import scala.util.Try
  import java.io.File
  import java.nio.file.{ Files, Paths ⇒ JPaths }

  import scala.language.{ higherKinds, postfixOps }

  object Local {
    def apply() = Effect(new Local)
  }

  class Local {

    def execute(cmd: String) = try {
      import sys.process._

      val out = new StringBuilder
      val err = new StringBuilder

      val (shell, commands) = BashShell.localBashCommand(cmd)

      val io = new ProcessIO(
        stdin ⇒ {
          stdin.write(commands.getBytes)
          stdin.close()
        },
        stdout ⇒ out append Source.fromInputStream(stdout).mkString(""),
        stderr ⇒ err append Source.fromInputStream(stderr).mkString(""))

      val proc = shell.run(io)
      ExecutionResult(proc.exitValue, out.mkString, err.mkString)

    } catch {
      case e: Throwable ⇒ throw LocalExecutionError(s"Error executing $cmd on local host", e)
    }

    def writeBytes(bytes: Array[Byte], path: String) = try {
      Files.write(JPaths.get(path), bytes): Unit
    } catch {
      case e: Throwable ⇒ throw LocalIOError(s"Could not write file to path $path on local host", e)
    }

    def writeFile(is: () ⇒ InputStream, path: String) = try {
      val ois = is()
      try Files.copy(ois, JPaths.get(path)): Unit finally ois.close()
    } catch {
      case e: Throwable ⇒ throw LocalIOError(s"Could not write file to path $path on local host", e)
    }

    def readFile[T](path: String, f: java.io.InputStream ⇒ T) = {
      val source = new FileInputStream(new File(path))
      try f(source) finally source.close()
    }

    def rmFile(path: String) = try {
      Files.delete(JPaths.get(path))
    } catch {
      case e: Throwable ⇒ throw LocalIOError(s"Could not delete file $path on local host", e)
    }

    def home() =
      try java.lang.System.getProperty("user.home")
      catch {
        case e: Throwable ⇒ throw LocalIOError(s"Could not determine homme on local host", e)
      }

    def exists(path: String) =
      try new File(path).exists
      catch {
        case e: Throwable ⇒ throw LocalIOError(s"Could not test if $path exists on local host", e)
      }

    def list(path: String) = try {
      new File(path).listFiles.map {
        f ⇒
          val ftype =
            if (Files.isSymbolicLink(f.toPath)) FileType.Link
            else if (f.isDirectory) FileType.Directory
            else FileType.File

          ListEntry(name = f.getName, `type` = ftype, modificationTime = Some(f.lastModified()))
      }.toList
    } catch {
      case e: Throwable ⇒ throw LocalIOError(s"Could not list directory $path on local host", e)
    }

    def makeDir(path: String) =
      try { new File(path).mkdirs: Unit }
      catch {
        case e: Throwable ⇒ throw LocalIOError(s"Could not make directory $path on local host", e)
      }

    def rmDir(path: String) = try {
      def delete(f: File): Unit = {
        if (f.isDirectory) f.listFiles.foreach(delete)
        f.delete
      }
      delete(new File(path))
    } catch {
      case e: Throwable ⇒ throw LocalIOError(s"Could not removet directory $path on local host", e)
    }

    def mv(from: String, to: String) = try {
      Files.move(JPaths.get(from), JPaths.get(to), StandardCopyOption.REPLACE_EXISTING)
    } catch {
      case e: Throwable ⇒ throw LocalIOError(s"Could not move $from to $to on local host", e)
    }

    def link(target: String, link: String, defaultOnCopy: Boolean) = {

      def createLink(target: JPath, link: JPath): JPath = {
        def getParentFileSafe(file: File): File =
          file.getParentFile() match {
            case null ⇒ if (file.isAbsolute) file else new File(".")
            case f    ⇒ f
          }

        def unsupported = {
          val fullTargetPath = if (target.isAbsolute) target else JPaths.get(getParentFileSafe(link.toFile).getPath, target.toFile.getPath)
          Files.copy(fullTargetPath, link, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING)
          link
        }

        try Files.createSymbolicLink(link, target)
        catch {
          case e: UnsupportedOperationException ⇒ if (defaultOnCopy) unsupported else throw e
          case e: FileAlreadyExistsException    ⇒ throw e
          case e: FileSystemException           ⇒ if (defaultOnCopy) unsupported else throw e
          case e: IOException                   ⇒ throw e
        }
      }

      createLink(JPaths.get(target), JPaths.get(link))
    }
  }

  case class LocalExecutionError(message: String, t: Throwable) extends Exception(message, t)
  case class LocalIOError(message: String, t: Throwable) extends Exception(message, t)

  //  @tagless trait Local {
  //    def execute(cmd: String): FS[ExecutionResult]
  //    def writeBytes(bytes: Array[Byte], path: String): FS[Unit]
  //    def writeFile(is: () ⇒ java.io.InputStream, path: String): FS[Unit]
  //    def rmFile(path: String): FS[Unit]
  //    def readFile[T](path: String, f: java.io.InputStream ⇒ T): FS[T]
  //    def home(): FS[String]
  //    def exists(path: String): FS[Boolean]
  //    def list(path: String): FS[List[ListEntry]]
  //    def makeDir(path: String): FS[Unit]
  //    def rmDir(path: String): FS[Unit]
  //    def mv(from: String, to: String): FS[Unit]
  //    def link(target: String, path: String, defaultOnCopy: Boolean): FS[Unit]
  //  }

  case class LocalHost() {
    override def toString = s"localhost"
  }

  def execute(cmd: String)(implicit local: Effect[Local]) = local().execute(cmd)

  def writeBytes(bytes: Array[Byte], path: String)(implicit local: Effect[Local]) = local().writeBytes(bytes, path)
  def writeFile(is: () ⇒ java.io.InputStream, path: String)(implicit local: Effect[Local]) = local().writeFile(is, path)
  def readFile[T](path: String, f: java.io.InputStream ⇒ T)(implicit local: Effect[Local]) = local().readFile(path, f)

  def rmFile(path: String)(implicit local: Effect[Local]): Unit = local().rmFile(path)
  def home(implicit local: Effect[Local]) = local().home
  def exists(path: String)(implicit local: Effect[Local]) = local().exists(path)
  def list(path: String)(implicit local: Effect[Local]) = local().list(path)
  def makeDir(path: String)(implicit local: Effect[Local]) = local().makeDir(path)
  def rmDir(path: String)(implicit local: Effect[Local]) = local().rmDir(path)
  def mv(from: String, to: String)(implicit local: Effect[Local]) = local().mv(from, to)
  def link(target: String, link: String, defaultOnCopy: Boolean = false)(implicit local: Effect[Local]) = local().link(target, link, defaultOnCopy)

}

