package gridscale

import gridscale.tools.shell.BashShell

package object local {

  import cats.implicits._
  import gridscale.tools._
  import freedsl.tool._
  import freestyle.tagless._
  import scala.io.Source
  import scala.util.Try
  import java.io.File
  import java.nio.file.{ Files, Paths }

  import scala.language.{ higherKinds, postfixOps }

  case class LocalInterpreter() extends Local.Handler[Try] {

    def execute(cmd: String) = Try {
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

    }.mapFailure(e ⇒ LocalExecutionError(s"Error executing $cmd on local host", e))

    def writeFile(bytes: Array[Byte], path: String) = Try {
      val discardedRes = Files.write(Paths.get(path), bytes)
    }.mapFailure(e ⇒ LocalIOError(s"Could not write file to path $path on local host", e))

    def readFile(path: String) = Try {
      val source = Source.fromFile(new File(path))
      try source.getLines mkString "\n" finally source.close()
    }.mapFailure(e ⇒ LocalIOError(s"Could not read file from path $path on local host", e))

    def rmFile(path: String) = Try(Files.delete(Paths.get(path))).mapFailure(e ⇒ LocalIOError(s"Could not delete file $path on local host", e))

    def home() =
      Try(System.getProperty("user.home")).mapFailure(e ⇒ LocalIOError(s"Could not determine homme on local host", e))

    def exists(path: String) =
      Try(new File(path).exists).mapFailure(e ⇒ LocalIOError(s"Could not test if $path exists on local host", e))

    def list(path: String) = Try {
      new File(path).listFiles.map {
        f ⇒
          val ftype =
            if (Files.isSymbolicLink(f.toPath)) FileType.Link
            else if (f.isDirectory) FileType.Directory
            else FileType.File

          ListEntry(name = f.getName, `type` = ftype, modificationTime = Some(f.lastModified()))
      }.toList
    }.mapFailure(e ⇒ LocalIOError(s"Could not list directory $path on local host", e))

    def makeDir(path: String) =
      Try { new File(path).mkdirs: Unit }.mapFailure(e ⇒ LocalIOError(s"Could not make directory $path on local host", e))

    def rmDir(path: String) = Try {
      def delete(f: File): Unit = {
        if (f.isDirectory) f.listFiles.foreach(delete)
        f.delete
      }
      delete(new File(path))
    }.mapFailure(e ⇒ LocalIOError(s"Could not removet directory $path on local host", e))

    def mv(from: String, to: String) = Try {
      new File(from).renameTo(new File(to)): Unit
    }.mapFailure(e ⇒ LocalIOError(s"Could not move $from to $to on local host", e))
  }

  case class LocalExecutionError(message: String, t: Throwable) extends Exception(message, t)
  case class LocalIOError(message: String, t: Throwable) extends Exception(message, t)

  @tagless trait Local {
    def execute(cmd: String): FS[ExecutionResult]
    def writeFile(bytes: Array[Byte], path: String): FS[Unit]
    def readFile(path: String): FS[String]
    def rmFile(path: String): FS[Unit]
    def home(): FS[String]
    def exists(path: String): FS[Boolean]
    def list(path: String): FS[List[ListEntry]]
    def makeDir(path: String): FS[Unit]
    def rmDir(path: String): FS[Unit]
    def mv(from: String, to: String): FS[Unit]
  }

  case class LocalHost() {
    override def toString = s"localhost"
  }

  def execute[M[_]](cmd: String)(implicit local: Local[M]) = local.execute(cmd)

  def writeFile[M[_]](bytes: Array[Byte], path: String)(implicit local: Local[M]) = local.writeFile(bytes, path)
  def readFile[M[_]](path: String)(implicit local: Local[M]) = local.readFile(path)
  def rmFile[M[_]](path: String)(implicit local: Local[M]): M[Unit] = local.rmFile(path)
  def home[M[_]: Local](implicit local: Local[M]) = local.home
  def exists[M[_]](path: String)(implicit local: Local[M]) = local.exists(path)
  def list[M[_]](path: String)(implicit local: Local[M]) = local.list(path)
  def makeDir[M[_]](path: String)(implicit local: Local[M]) = local.makeDir(path)
  def rmDir[M[_]](path: String)(implicit local: Local[M]) = local.rmDir(path)
  def mv[M[_]](from: String, to: String)(implicit local: Local[M]) = local.mv(from, to)

}

