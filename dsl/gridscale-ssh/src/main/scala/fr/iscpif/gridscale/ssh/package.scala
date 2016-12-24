package fr.iscpif.gridscale

import freedsl.dsl._
import freedsl.util._
import cats._
import cats.data._
import cats.implicits._
import fr.iscpif.gridscale.ssh.sshj.{ SFTPClient, SSHClient }
import squants._
import time.TimeConversions._

package object ssh {

  case class ExecutionResult(returnCode: Int, stdOut: String, stdErr: String)

  object SSH {

    case class ConnectionError(t: Throwable) extends Error

    case class ExecutionError(t: Throwable) extends Error

    type Authentication = SSHClient ⇒ util.Try[Unit]

    case class Client(client: SSHClient, sFTPClient: SFTPClient) {
      def close() = {
        sFTPClient.close()
        client.close()
      }
    }

    def client(
      server: Server,
      authentication: Authentication,
      timeout: Time = 1 minutes): util.Either[ConnectionError, Client] = {
      val ssh = new SSHClient

      // disable strict host key checking
      ssh.disableHostChecking()
      ssh.useCompression()
      ssh.connect(server.host, server.port)

      authentication(ssh) match {
        case util.Success(_) ⇒
          ssh.setConnectTimeout(timeout.millis.toInt)
          ssh.setTimeout(timeout.millis.toInt)
          util.Right(Client(ssh, ssh.newSFTPClient))
        case util.Failure(e) ⇒
          ssh.disconnect()
          util.Left(ConnectionError(e))
      }
    }

    def interpreter(client: util.Either[ConnectionError, Client]) = new Interpreter[Id] {

      def interpret[_] = {
        case execute(s) ⇒
          for {
            c ← client
            r ← SSHClient.exec(c.client)(s).toEither.leftMap(t ⇒ ExecutionError(t))
          } yield r
        case fileExists(path) ⇒
          client.map(_.sFTPClient.exists(path))
        case readFile(path, f) ⇒
          client.map { c ⇒
            val is = c.sFTPClient.readAheadFileInputStream(path)
            try f(is) finally is.close
          }
        case wrongReturnCode(command, executionResult) ⇒ Left(ReturnCodeError(command, executionResult))
      }
    }

    case class ReturnCodeError(command: String, executionResult: ExecutionResult) extends Error {
      import executionResult._
      override def toString = s"Unexpected return code $returnCode, when running $command (stdout=${executionResult.stdOut}, stderr=${executionResult.stdErr})"
    }

  }

  @dsl trait SSH[M[_]] {
    def execute(s: String): M[ExecutionResult]
    def fileExists(path: String): M[Boolean]
    def readFile[T](path: String, f: java.io.InputStream ⇒ T): M[T]
    def wrongReturnCode(command: String, executionResult: ExecutionResult): M[Unit]
  }

  case class Server(host: String, port: Int = 22)
  case class JobId(jobId: String, workDirectory: String)

  def submit[M[_]: Monad: Util](description: SSHJobDescription)(implicit ssh: SSH[M]) = for {
    j ← SSHJobDescription.toScript[M](description)
    (command, jobId) = j
    _ ← ssh.execute(command)
  } yield JobId(jobId, description.workDirectory)

  def stdOut[M[_]](jobId: JobId)(implicit ssh: SSH[M]) =
    ssh.readFile(
      SSHJobDescription.outFile(jobId.workDirectory, jobId.jobId),
      io.Source.fromInputStream(_).mkString)

  def stdErr[M[_]](jobId: JobId)(implicit ssh: SSH[M]) =
    ssh.readFile(
      SSHJobDescription.errFile(jobId.workDirectory, jobId.jobId),
      io.Source.fromInputStream(_).mkString)

  def state[M[_]: Monad](jobId: JobId)(implicit ssh: SSH[M]) =
    SSHJobDescription.jobIsRunning[M](jobId).flatMap {
      case true ⇒ (JobState.Running: JobState).pure[M]
      case false ⇒
        ssh.fileExists(SSHJobDescription.endCodeFile(jobId.workDirectory, jobId.jobId)).flatMap {
          case true ⇒
            for {
              // FIXME Limit the size of the read
              content ← ssh.readFile(
                SSHJobDescription.endCodeFile(jobId.workDirectory, jobId.jobId),
                is ⇒ io.Source.fromInputStream(is).mkString)
            } yield SSHJobDescription.translateState(content.takeWhile(_.isDigit).toInt)
          case false ⇒ (JobState.Failed: JobState).pure[M]
        }
    }

  def clean[M[_]: Monad](job: JobId)(implicit ssh: SSH[M]) = {
    val kill = s"kill `cat ${SSHJobDescription.pidFile(job.workDirectory, job.jobId)}`;"
    val rm = s"rm -rf ${job.workDirectory}/${job.jobId}*"
    for {
      k ← ssh.execute(kill)
      _ ← ssh.execute(rm)
      _ ← k.returnCode match {
        case 0 | 1 ⇒ ().pure[M]
        case _     ⇒ ssh.wrongReturnCode(kill, k)
      }
    } yield ()
  }

  def fileExists[M[_]](path: String)(implicit ssh: SSH[M]) =
    ssh.fileExists(path)

  def readFile[M[_], T](path: String, f: java.io.InputStream ⇒ T)(implicit ssh: SSH[M]) =
    ssh.readFile(path, f)

  case class SSHJobDescription(command: String, workDirectory: String)

  object SSHJobDescription {

    def jobIsRunning[M[_]: Monad](job: JobId)(implicit ssh: SSH[M]) = {
      val cde = s"ps -p `cat ${pidFile(job.workDirectory, job.jobId)}`"
      ssh.execute(cde).map(_.returnCode == 0)
    }

    def translateState(retCode: Int): JobState =
      retCode match {
        case 0 ⇒ JobState.Done
        case _ ⇒ JobState.Failed
      }

    def file(dir: String, jobId: String, suffix: String) = dir + "/" + jobId + "." + suffix
    def pidFile(dir: String, jobId: String) = file(dir, jobId, "pid")
    def endCodeFile(dir: String, jobId: String) = file(dir, jobId, "end")
    def outFile(dir: String, jobId: String) = file(dir, jobId, "out")
    def errFile(dir: String, jobId: String) = file(dir, jobId, "err")

    // FIXME bash shell.
    def toScript[M[_]: Monad](description: SSHJobDescription, background: Boolean = true)(implicit utilM: Util[M]) = {
      for {
        jobId ← utilM.randomUUID.map(_.toString)
      } yield {
        def absolute(path: String) = description.workDirectory + "/" + path
        def executable = description.command

        def command =
          s"""
             |mkdir -p ${description.workDirectory}
             |cd ${description.workDirectory}
             |($executable >${outFile(description.workDirectory, jobId)} 2>${errFile(description.workDirectory, jobId)} ; echo \\$$? >${endCodeFile(description.workDirectory, jobId)}) ${if (background) "&" else ""}
             |echo \\$$! >${pidFile(description.workDirectory, jobId)}
           """.stripMargin

        def shellCommand =
          s"""
            |bash -ci bash <<EOF
            |$command
            |EOF
          """.stripMargin

        (shellCommand, jobId)
      }
    }
  }

}

