package gridscale

import java.io.ByteArrayInputStream

import freestyle.tagless.tagless
import gridscale.tools.shell.BashShell
import net.schmizz.sshj.connection.channel.direct.Session.Command
import freedsl.dsl._
import scala.util.Try
import scala.language.{ higherKinds, postfixOps }

package object ssh {

  import cats._
  import cats.implicits._
  import freedsl.tool._
  import gridscale.ssh.sshj.{ SFTPClient, SSHClient }
  import gridscale.authentication._
  import freedsl.system._
  import squants._
  import time.TimeConversions._
  import gridscale.tools.cache._

  object SSHAuthentication {

    implicit def userPassword = new SSHAuthentication[UserPassword] {
      override def login(t: UserPassword) = t.user
      override def authenticate(t: UserPassword, sshClient: SSHClient): Try[Unit] =
        sshClient.authPassword(t.user, t.password)
    }

    implicit def key = new SSHAuthentication[PrivateKey] {
      override def login(t: PrivateKey) = t.user
      override def authenticate(t: PrivateKey, sshClient: SSHClient): Try[Unit] =
        sshClient.authPrivateKey(t)
    }

  }

  trait SSHAuthentication[T] {
    def login(t: T): String
    def authenticate(t: T, sshClient: SSHClient): util.Try[Unit]
  }

  object SSHInterpreter {
    def apply[T](f: SSHInterpreter ⇒ T) = {
      val intp = new SSHInterpreter
      try f(intp)
      finally intp.close()
    }
  }

  case class SSHInterpreter() extends SSH.Handler[Evaluated] with AutoCloseable {

    def client(server: SSHServer): Evaluated[SSHClient] = {
      val ssh =
        guard {
          val ssh = new SSHClient
          // disable strict host key checking
          ssh.disableHostChecking()
          ssh.useCompression()
          ssh.setConnectTimeout(server.timeout.millis.toInt)
          ssh.setTimeout(server.timeout.millis.toInt)
          ssh.connect(server.host, server.port)
          ssh
        }.leftMap { t ⇒ ConnectionError(s"Error connecting to $server", t) }

      def authenticate(ssh: SSHClient) = {
        server.authenticate(ssh) match {
          case util.Success(s) ⇒ result(ssh)
          case util.Failure(e) ⇒
            guard(ssh.disconnect())
            error(AuthenticationException(s"Error authenticating to $server", e))
        }
      }

      for {
        client ← ssh
        a ← authenticate(client)
      } yield a
    }

    val clientCache = KeyValueCache(client)

    def execute(server: SSHServer, s: String) =
      for {
        c ← clientCache.get(server)
        r ← SSHClient.run(c, s).toEither.leftMap(t ⇒ ExecutionError(s"Error executing $s on $server", t))
      } yield r

    def launch(server: SSHServer, s: String) =
      for {
        c ← clientCache.get(server)
        _ = SSHClient.launchInBackground(c, s)
      } yield ()

    def sftp[T](server: SSHServer, f: SFTPClient ⇒ util.Try[T]) =
      for {
        c ← clientCache.get(server)
        r ← SSHClient.sftp(c, f).flatten.toEither.leftMap(t ⇒ SFTPError(s"Error in sftp transfer on $server", t))
      } yield r

    def readFile[T](server: SSHServer, path: String, f: java.io.InputStream ⇒ T) =
      for {
        c ← clientCache.get(server)
        res ← SSHClient.sftp(c, s ⇒ s.readAheadFileInputStream(path).map(f)).flatten.toEither.leftMap(t ⇒ SFTPError(s"Error in sftp transfer on $server", t))
      } yield res

    def writeFile(server: SSHServer, is: () ⇒ java.io.InputStream, path: String) = {
      def write(client: SSHClient) = {
        val ois = is()
        try SSHClient.sftp(client, _.writeFile(ois, path))
        finally ois.close()
      }

      for {
        c ← clientCache.get(server)
        _ ← write(c).toEither
      } yield ()
    }

    def wrongReturnCode(server: String, command: String, executionResult: ExecutionResult) = error(ReturnCodeError(server, command, executionResult))

    def close() = {
      clientCache.values.flatMap(_.toOption).foreach(_.close())
      clientCache.clear()
      ()
    }

  }

  case class ConnectionError(message: String, t: Throwable) extends Exception(message, t)
  case class ExecutionError(message: String, t: Throwable) extends Exception(message, t)
  case class SFTPError(message: String, t: Throwable) extends Exception(message, t)
  case class ReturnCodeError(server: String, command: String, executionResult: ExecutionResult) extends Exception {
    override def toString = ExecutionResult.error(command, executionResult) + s" on server $server"
  }

  @tagless trait SSH {
    def launch(server: SSHServer, s: String): FS[Unit]
    def execute(server: SSHServer, s: String): FS[ExecutionResult]
    def sftp[T](server: SSHServer, f: SFTPClient ⇒ util.Try[T]): FS[T]
    def readFile[T](server: SSHServer, path: String, f: java.io.InputStream ⇒ T): FS[T]
    def writeFile(server: SSHServer, is: () ⇒ java.io.InputStream, path: String): FS[Unit]
    def wrongReturnCode(server: String, command: String, executionResult: ExecutionResult): FS[Unit]
  }

  object SSHServer {
    def apply[A: SSHAuthentication](host: String, port: Int = 22, timeout: Time = 1 minutes)(authentication: A): SSHServer = {
      val sSHAuthentication = implicitly[SSHAuthentication[A]]
      new SSHServer(sSHAuthentication.login(authentication), host, port)(timeout, (sshClient: SSHClient) ⇒ sSHAuthentication.authenticate(authentication, sshClient))
    }
  }

  case class SSHServer(login: String, host: String, port: Int)(val timeout: Time, val authenticate: SSHClient ⇒ Try[Unit]) {
    override def toString = s"ssh server $host:$port"

  }

  case class JobId(jobId: String, workDirectory: String)

  /* ----------------------- Job managment --------------------- */

  def submit[M[_]: Monad: System](server: SSHServer, description: SSHJobDescription)(implicit ssh: SSH[M]) = for {
    j ← SSHJobDescription.jobScript[M](server, description)
    (command, jobId) = j
    _ ← ssh.launch(server, command)
  } yield JobId(jobId, description.workDirectory)

  def run[M[_]: Monad](server: SSHServer, command: String, verbose: Boolean = false)(implicit ssh: SSH[M]) = for {
    _ ← ().pure[M]
    r ← ssh.execute(server, SSHJobDescription.commandLine(server, command, verbose))
  } yield r

  def stdOut[M[_]](server: SSHServer, jobId: JobId)(implicit ssh: SSH[M]) =
    readFile(
      server,
      SSHJobDescription.outFile(jobId.workDirectory, jobId.jobId),
      io.Source.fromInputStream(_).mkString)

  def stdErr[M[_]](server: SSHServer, jobId: JobId)(implicit ssh: SSH[M]) =
    readFile(
      server,
      SSHJobDescription.errFile(jobId.workDirectory, jobId.jobId),
      io.Source.fromInputStream(_).mkString)

  def state[M[_]: Monad](server: SSHServer, jobId: JobId)(implicit ssh: SSH[M]) = for {
    s ← SSHJobDescription.jobIsRunning[M](server, jobId).flatMap {
      case true ⇒ (JobState.Running: JobState).pure[M]
      case false ⇒
        exists[M](server, SSHJobDescription.endCodeFile(jobId.workDirectory, jobId.jobId)).flatMap {
          case true ⇒
            for {
              // FIXME Limit the size of the read
              content ← ssh.readFile(
                server,
                SSHJobDescription.endCodeFile(jobId.workDirectory, jobId.jobId),
                is ⇒ io.Source.fromInputStream(is).mkString)
              exitCode = content.takeWhile(_.isDigit).toInt
            } yield SSHJobDescription.translateState(exitCode)
          case false ⇒ (JobState.Failed: JobState).pure[M]
        }
    }
  } yield s

  def clean[M[_]: Monad](server: SSHServer, job: JobId)(implicit ssh: SSH[M]) = {
    val kill = s"pid=`cat ${SSHJobDescription.pidFile(job.workDirectory, job.jobId)}` ; kill -9 $$pid `ps --ppid $$pid -o pid=`"
    val rm = s"rm -rf ${job.workDirectory}/${job.jobId}*"
    for {
      k ← ssh.execute(server, kill)
      _ ← ssh.execute(server, rm)
      _ ← k.returnCode match {
        case 0 | 1 ⇒ ().pure[M]
        case _     ⇒ ssh.wrongReturnCode(server.toString, kill, k)
      }
    } yield ()
  }

  case class SSHJobDescription(command: String, workDirectory: String)

  object SSHJobDescription {

    def jobIsRunning[M[_]: Monad](server: SSHServer, job: JobId)(implicit ssh: SSH[M]) = {
      val cde = s"kill -0 `cat ${pidFile(job.workDirectory, job.jobId)}`"
      for {
        _ ← ().pure[M] // Force execute to be called for some reason
        r ← ssh.execute(server, cde)
      } yield (r.returnCode == 0)
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
    def scriptFile(dir: String, jobId: String) = file(dir, jobId, "sh")

    def commandLine(server: SSHServer, command: String, verbose: Boolean = false) =
      s"""
             |env -i bash ${if (verbose) "-x" else ""} <<EOF
             |${BashShell.source}
             |${command}
             |EOF
             |""".stripMargin

    def jobScript[M[_]: Monad: SSH](server: SSHServer, description: SSHJobDescription)(implicit system: System[M]) = {
      def script(jobId: String) =
        s"""
           |mkdir -p ${description.workDirectory}
           |cd ${description.workDirectory}
           |${BashShell.source}
           |
           |{
           |  ${description.command} >${outFile(description.workDirectory, jobId)} 2>${errFile(description.workDirectory, jobId)}
           |  echo $$? >${endCodeFile(description.workDirectory, jobId)}
           |} & pid=$$!
           |
           |echo $$pid >${pidFile(description.workDirectory, jobId)}
           |
           |""".stripMargin

      //      def run(jobId: String) =
      //        s"""
      //         |env -i bash <<EOF
      //         |source /etc/profile 2>/dev/null
      //         |source ~/.bash_profile 2>/dev/null
      //         |source ~/.bash_login 2>/dev/null
      //         |source ~/.profile 2>/dev/null
      //         |${script(jobId)}
      //         |EOF
      //         |""".stripMargin

      for {
        jobId ← system.randomUUID.map(_.toString)
        file = scriptFile(description.workDirectory, jobId)
        _ ← writeFile[M](server, () ⇒ new ByteArrayInputStream(script(jobId).getBytes), file)
      } yield (s"""/bin/bash $file""", jobId)
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
        } asJava)
    }
  }

  //  def writeBytes[M[_]](bytes: Array[Byte], path: String)(implicit local: Local[M]) = local.writeBytes(bytes, path)
  //  def writeFile[M[_]](file: File, path: String, streamModifier: Option[OutputStream ⇒ OutputStream] = None)(implicit local: Local[M]) = local.writeFile(file, path, streamModifier)
  //  def readFile[M[_], T](path: String, f: java.io.InputStream ⇒ T)(implicit local: Local[M]) = local.readFile(path, f)

  def readFile[M[_], T](server: SSHServer, path: String, f: java.io.InputStream ⇒ T)(implicit ssh: SSH[M]) = ssh.readFile(server, path, f)
  def writeFile[M[_]](server: SSHServer, is: () ⇒ java.io.InputStream, path: String)(implicit ssh: SSH[M]): M[Unit] = ssh.writeFile(server, is, path)

  def home[M[_]](server: SSHServer)(implicit ssh: SSH[M]) = ssh.sftp(server, _.canonicalize("."))
  def exists[M[_]](server: SSHServer, path: String)(implicit ssh: SSH[M]) = ssh.sftp(server, _.exists(path))
  def chmod[M[_]](server: SSHServer, path: String, perms: FilePermission.FilePermission*)(implicit ssh: SSH[M]) =
    ssh.sftp(server, _.chmod(path, FilePermission.toMask(perms.toSet[FilePermission.FilePermission])))

  def list[M[_]](server: SSHServer, path: String)(implicit ssh: SSH[M]) = ssh.sftp(server, _.ls(path)(e ⇒ e != "." || e != ".."))

  def makeDir[M[_]](server: SSHServer, path: String)(implicit ssh: SSH[M]) = ssh.sftp(server, _.mkdir(path))

  def rmDir[M[_]: Monad](server: SSHServer, path: String)(implicit ssh: SSH[M]): M[Unit] = {
    import cats.implicits._

    def remove(entry: ListEntry): M[Unit] = {
      import FileType._
      val child = path + "/" + entry.name
      entry.`type` match {
        case File      ⇒ rmFile[M](server, child)
        case Link      ⇒ rmFile[M](server, child)
        case Directory ⇒ rmDir[M](server, child)
        case Unknown   ⇒ rmFile[M](server, child)
      }
    }

    for {
      entries ← list[M](server, path)
      _ ← entries.traverse(remove)
      _ ← ssh.sftp(server, _.rmdir(path))
    } yield ()
  }

  def rmFile[M[_]](server: SSHServer, path: String)(implicit ssh: SSH[M]) = ssh.sftp(server, _.rm(path))
  def mv[M[_]](server: SSHServer, from: String, to: String)(implicit ssh: SSH[M]) = ssh.sftp(server, _.rename(from, to))

}

