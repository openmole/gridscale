package fr.iscpif.gridscale

import freedsl.dsl._
import freedsl.util._
import cats._
import cats.data._
import cats.implicits._
import fr.iscpif.gridscale.ssh.sshj.{ SFTPClient, SSHClient }
import fr.iscpif.gridscale.tools._

import scala.concurrent.duration._

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
      timeout: Duration = 1 minute): util.Either[ConnectionError, Client] = {
      val ssh = new SSHClient

      // disable strict host key checking
      ssh.disableHostChecking()
      ssh.useCompression()
      ssh.connect(server.host, server.port)

      authentication(ssh) match {
        case util.Success(_) ⇒
          ssh.setConnectTimeout(timeout.toMillis.toInt)
          ssh.setTimeout(timeout.toMillis.toInt)
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
      }
    }
  }

  @dsl trait SSH[M[_]] {
    def execute(s: String): M[ExecutionResult]
    def fileExists(path: String): M[Boolean]
    def readFile[T](path: String, f: java.io.InputStream ⇒ T): M[T]
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

  //    def state[M[_]](jobId: JobId) =
  //        if (jobIsRunning(job)) Running
  //        else {
  //          if (exists(endCodeFile(job.workDirectory, job.jobId))) {
  //            val is = _read(endCodeFile(job.workDirectory, job.jobId))
  //            val content =
  //              try getBytes(is, bufferSize, timeout)
  //              finally is.close
  //
  //            translateState(new String(content).takeWhile(_.isDigit).toInt)
  //          } else Failed
  //        }

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

