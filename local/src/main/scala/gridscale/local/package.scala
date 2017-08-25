package gridscale

import gridscale.tools.shell.BashShell

package object local {

  import cats.implicits._
  import freedsl.dsl._
  import scala.io.Source
  import scala.util.Try
  import java.io.File
  import java.nio.file.{ Files, Paths }

  import scala.language.{ higherKinds, postfixOps }

  object Local {

    def interpreter = new Interpreter {

      def execute(cmd: String)(implicit context: Context) = Try {
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

      }.toEither.leftMap(e ⇒ LocalExecutionError(s"Error executing $cmd on local host", e))

      def writeFile(bytes: Array[Byte], path: String)(implicit context: Context) = Try {
        val discardedRes = Files.write(Paths.get(path), bytes)
      }.toEither.leftMap(e ⇒ LocalIOError(s"Could not write file to path $path on local host", e))

      def readFile(path: String)(implicit context: Context) = Try {
        val source = Source.fromFile(new File(path))
        try source.getLines mkString "\n" finally source.close()
      }.toEither.leftMap(e ⇒ LocalIOError(s"Could not read file from path $path on local host", e))

      def rm(path: String)(implicit context: Context) = Try(Files.delete(Paths.get(path))).toEither.leftMap(e ⇒ LocalIOError(s"Could not delete file $path on local host", e))

    }

    case class LocalExecutionError(message: String, t: Throwable) extends Exception(message, t) with Error
    case class LocalIOError(message: String, t: Throwable) extends Exception(message, t) with Error

  }

  @dsl trait Local[M[_]] {
    def execute(cmd: String): M[ExecutionResult]
    def writeFile(bytes: Array[Byte], path: String): M[Unit]
    def readFile(path: String): M[String]
    def rm(path: String): M[Unit]
  }

  case class LocalHost() {
    override def toString = s"localhost"
  }

  def execute[M[_]](cmd: String)(implicit local: Local[M]) = local.execute(cmd)

  def writeFile[M[_]](bytes: Array[Byte], path: String)(implicit local: Local[M]) = local.writeFile(bytes, path)

  def readFile[M[_]](path: String)(implicit local: Local[M]) = local.readFile(path)

  def rm[M[_]](path: String)(implicit local: Local[M]): M[Unit] = local.rm(path)

}

