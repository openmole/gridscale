package gridscale

import scala.util.Try

package object ssh {

  import cats._
  import cats.implicits._
  import gridscale.ssh.sshj.{ SFTPClient, SSHClient }
  import gridscale.authentication._
  import freedsl.dsl._
  import freedsl.system._
  import squants._
  import time.TimeConversions._

  case class ExecutionResult(returnCode: Int, stdOut: String, stdErr: String)

  object SSH {

    object Authentication {

      implicit def userPassword = new Authentication[UserPassword] {
        override def authenticate(t: UserPassword, sshClient: SSHClient): Try[Unit] =
          sshClient.authPassword(t.user, t.password)
      }

      implicit def key = new Authentication[PrivateKey] {
        override def authenticate(t: PrivateKey, sshClient: SSHClient): Try[Unit] =
          sshClient.authPrivateKey(t)
      }

    }

    trait Authentication[T] {
      def authenticate(t: T, sshClient: SSHClient): util.Try[Unit]
    }

    case class Client(client: SSHClient, sFTPClient: SFTPClient) {
      def close() = {
        try sFTPClient.close()
        finally client.close()
      }
    }

    def client[A: Authentication](
      server: Server,
      authentication: A,
      timeout: Time = 1 minutes): util.Either[ConnectionError, Client] = {

      val ssh =
        util.Try {
          val ssh = new SSHClient
          // disable strict host key checking
          ssh.disableHostChecking()
          ssh.useCompression()
          ssh.connect(server.host, server.port)
          ssh
        }.toEither.leftMap(t ⇒ ConnectionError(t))

      def authenticate(ssh: SSHClient) =
        implicitly[Authentication[A]].authenticate(authentication, ssh) match {
          case util.Success(_) ⇒
            ssh.setConnectTimeout(timeout.millis.toInt)
            ssh.setTimeout(timeout.millis.toInt)
            util.Right(Client(ssh, ssh.newSFTPClient))
          case util.Failure(e) ⇒
            ssh.disconnect()
            util.Left(ConnectionError(e))
        }

      for {
        client ← ssh
        a ← authenticate(client)
      } yield a
    }

    def interpreter(client: util.Either[ConnectionError, Client]) = new Interpreter[Id] {

      def sftpClient = client.map(_.sFTPClient)

      def interpret[_] = {
        case execute(s) ⇒
          for {
            c ← client
            r ← SSHClient.exec(c.client)(s).toEither.leftMap(t ⇒ ExecutionError(t))
          } yield r

        case sftp(f) ⇒
          for {
            c ← sftpClient
            r ← f(c).toEither.leftMap(t ⇒ SFTPError(t))
          } yield r

        case readFile(path, f) ⇒
          for {
            c ← sftpClient
            is ← c.readAheadFileInputStream(path).toEither.leftMap(t ⇒ SFTPError(t))
            res = try f(is) finally is.close
          } yield res

        case wrongReturnCode(command, executionResult) ⇒ Left(ReturnCodeError(command, executionResult))
      }
    }

    case class ConnectionError(t: Throwable) extends Error
    case class ExecutionError(t: Throwable) extends Error
    case class SFTPError(t: Throwable) extends Error
    case class ReturnCodeError(command: String, executionResult: ExecutionResult) extends Error {
      import executionResult._
      override def toString = s"Unexpected return code $returnCode, when running $command (stdout=${executionResult.stdOut}, stderr=${executionResult.stdErr})"
    }

  }

  @dsl trait SSH[M[_]] {
    def execute(s: String): M[ExecutionResult]
    def sftp[T](f: SFTPClient ⇒ util.Try[T]): M[T]
    def readFile[T](path: String, f: java.io.InputStream ⇒ T): M[T]
    def wrongReturnCode(command: String, executionResult: ExecutionResult): M[Unit]
  }

  case class Server(host: String, port: Int = 22)
  case class JobId(jobId: String, workDirectory: String)

  /* ----------------------- Job managment --------------------- */

  def submit[M[_]: Monad: System](description: SSHJobDescription)(implicit ssh: SSH[M]) = for {
    j ← SSHJobDescription.toScript[M](description)
    (command, jobId) = j
    _ ← ssh.execute(command)
  } yield JobId(jobId, description.workDirectory)

  def stdOut[M[_]](jobId: JobId)(implicit ssh: SSH[M]) =
    readFile(
      SSHJobDescription.outFile(jobId.workDirectory, jobId.jobId),
      io.Source.fromInputStream(_).mkString)

  def stdErr[M[_]](jobId: JobId)(implicit ssh: SSH[M]) =
    readFile(
      SSHJobDescription.errFile(jobId.workDirectory, jobId.jobId),
      io.Source.fromInputStream(_).mkString)

  def state[M[_]: Monad](jobId: JobId)(implicit ssh: SSH[M]) =
    SSHJobDescription.jobIsRunning[M](jobId).flatMap {
      case true ⇒ (JobState.Running: JobState).pure[M]
      case false ⇒
        fileExists[M](SSHJobDescription.endCodeFile(jobId.workDirectory, jobId.jobId)).flatMap {
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
    def toScript[M[_]: Monad](description: SSHJobDescription, background: Boolean = true)(implicit system: System[M]) = {
      for {
        jobId ← system.randomUUID.map(_.toString)
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

  /* ------------------------ sftp ---------------------------- */

  object FilePermission {

    sealed abstract class FilePermission
    case object USR_RWX extends FilePermission
    case object GRP_RWX extends FilePermission
    case object OTH_RWX extends FilePermission

    def toMask(fps: Set[FilePermission]): Int = {

      import net.schmizz.sshj.xfer.{ FilePermission ⇒ SSHJFilePermission }

      import collection.JavaConverters._

      SSHJFilePermission.toMask(
        fps map {
          case USR_RWX ⇒ SSHJFilePermission.USR_RWX
          case GRP_RWX ⇒ SSHJFilePermission.GRP_RWX
          case OTH_RWX ⇒ SSHJFilePermission.OTH_RWX
        } asJava
      )
    }
  }

  def fileExists[M[_]](path: String)(implicit ssh: SSH[M]) = ssh.sftp(_.exists(path))
  def readFile[M[_], T](path: String, f: java.io.InputStream ⇒ T)(implicit ssh: SSH[M]) = ssh.readFile(path, f)
  def writeFile[M[_]](is: java.io.InputStream, path: String)(implicit ssh: SSH[M]): M[Unit] = ssh.sftp(_.writeFile(is, path))
  def home[M[_]](implicit ssh: SSH[M]) = ssh.sftp(_.canonicalize("."))
  def exists[M[_]](path: String)(implicit ssh: SSH[M]) = ssh.sftp(_.exists(path))
  def chmod[M[_]](path: String, perms: FilePermission.FilePermission*)(implicit ssh: SSH[M]) =
    ssh.sftp(_.chmod(path, FilePermission.toMask(perms.toSet[FilePermission.FilePermission])))
  def list[M[_]](path: String)(implicit ssh: SSH[M]) = ssh.sftp { _.ls(path)(e ⇒ e == "." || e == "..") }
  def makeDir[M[_]](path: String)(implicit ssh: SSH[M]) = ssh.sftp(_.mkdir(path))

  def rmDir[M[_]: Monad](path: String)(implicit ssh: SSH[M]): M[Unit] = {
    import cats.implicits._

    def remove(entry: ListEntry): M[Unit] = {
      import FileType._
      val child = path + "/" + entry.name
      entry.`type` match {
        case File      ⇒ rmFile[M](child)
        case Link      ⇒ rmFile[M](child)
        case Directory ⇒ rmDir[M](child)
        case Unknown   ⇒ rmFile[M](child)
      }
    }

    for {
      entries ← list[M](path)
      _ ← entries.traverse(remove)
      _ ← ssh.sftp(_.rmdir(path))
    } yield ()
  }

  def rmFile[M[_]](path: String)(implicit ssh: SSH[M]) = ssh.sftp(_.rm(path))
  def mv[M[_]](from: String, to: String)(implicit ssh: SSH[M]) = ssh.sftp(_.rename(from, to))

}

