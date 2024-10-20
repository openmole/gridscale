package gridscale.ssh

import java.io.ByteArrayInputStream
import gridscale.*
import gridscale.tools.shell.BashShell

import scala.language.{higherKinds, postfixOps}
import gridscale.ssh.sshj.{SFTPClient, SSHClient}
import gridscale.authentication.*
import squants.*
import time.TimeConversions.*
import gridscale.tools.cache.*

import java.util.UUID

object SSHAuthentication {

  implicit def userPassword: SSHAuthentication[UserPassword] = new SSHAuthentication[UserPassword] {
    override def login(t: UserPassword) = t.user
    override def authenticate(t: UserPassword, sshClient: SSHClient): Unit =
      sshClient.authPassword(t.user, t.password)
  }

  implicit def key: SSHAuthentication[PrivateKey]  = new SSHAuthentication[PrivateKey] {
    override def login(t: PrivateKey) = t.user
    override def authenticate(t: PrivateKey, sshClient: SSHClient): Unit =
      sshClient.authPrivateKey(t)
  }

}

trait SSHAuthentication[T] {
  def login(t: T): String
  def authenticate(t: T, sshClient: SSHClient): Unit
}

object SSH {

  def withSSH[T](f: SSH ?=> T): T =
    val ssh = SSH()
    try f(using ssh)
    finally ssh.close()

  def apply(connectionCache: ConnectionCache = SSHCache()): SSH = new SSH(connectionCache)

  def authenticate(server: SSHServer, client: SSHClient): SSHClient =
    try
      server.authenticate(client)
      client
    catch
      case e: Throwable ⇒
        util.Try(client.disconnect())
        throw AuthenticationException(s"Error authenticating to $server", e)

  def client(server: SSHServer): SSHClient =
    def ssh: SSHClient =
      try {
        val jumpClient =
          server.sshProxy.map { proxy =>
            val client = new SSHClient(
                host = proxy.host,
                port = proxy.port,
                timeout = proxy.timeout,
                keepAlive = proxy.keepAlive)

            SSHClient.connect(client)
            authenticate(proxy, client)
          }

        val ssh = new SSHClient(host = server.host, port = server.port, timeout = server.timeout, keepAlive = server.keepAlive, proxyJump = jumpClient)
        SSHClient.connect(ssh)
      } catch {
        case t: Throwable ⇒ throw ConnectionError(s"Error connecting to $server", t)
      }

    authenticate(server, ssh)
}

type ConnectionCache = KeyValueCache[SSHServer, SSHClient]

object SSHCache {
  def apply(): KeyValueCache[SSHServer, SSHClient] = KeyValueCache(s ⇒ SSH.client(s))

  def withCache[T](cache: ConnectionCache, server: SSHServer)(f: SSHClient ⇒ T): T = {
    val client = cache.getValidOrInvalidate(server, _.isConnected, c ⇒ util.Try(c.close()))
    f(client)
  }
}

class SSH(val clientCache: ConnectionCache) extends AutoCloseable {

  def execute(server: SSHServer, s: String) = SSHCache.withCache(clientCache, server) { c ⇒
    try SSHClient.run(c, s).get
    catch
      case t: Throwable ⇒ throw ExecutionError(s"Error executing $s on $server", t)
  }

  def launch(server: SSHServer, s: String) = SSHCache.withCache(clientCache, server) { c ⇒
    SSHClient.launchInBackground(c, s)
  }

  def withSFTP[T](server: SSHServer, f: SFTPClient ⇒ T): T = SSHCache.withCache(clientCache, server) { c ⇒
    try SSHClient.withSFTP(c, f)
    catch
      case t: Throwable ⇒ throw SFTPError(s"Error in sftp transfer on $server", t)
  }

  def readFile[T](server: SSHServer, path: String, f: java.io.InputStream ⇒ T) = SSHCache.withCache(clientCache, server) { c ⇒
    try SSHClient.withSFTP(c, s ⇒ s.readAheadFileInputStream(path).map(f)).get
    catch
      case t: SFTPError => throw t
      case t: Throwable ⇒ throw SFTPError(s"Error while reading $path via sftp on $server", t)
  }

  def writeFile(server: SSHServer, is: () ⇒ java.io.InputStream, path: String) = {
    def write(client: SSHClient) = {
      val ois = is()
      try SSHClient.withSFTP(client, _.writeFile(ois, path)).get
      finally ois.close()
    }

    try SSHCache.withCache(clientCache, server) { c ⇒ write(c) }
    catch
      case t: SFTPError => throw t
      case t: Throwable ⇒ throw SFTPError(s"Error while writing to $path via sftp on $server", t)
  }

  def wrongReturnCode(server: String, command: String, executionResult: ExecutionResult) = throw ReturnCodeError(server, command, executionResult)

  def close(): Unit =
    clientCache.values.foreach(c ⇒ util.Try(c.close()))
    clientCache.clear()

}

case class ConnectionError(message: String, t: Throwable) extends Exception(message, t)
case class ExecutionError(message: String, t: Throwable) extends Exception(message, t)
case class SFTPError(message: String, t: Throwable) extends Exception(message, t)

case class ReturnCodeError(server: String, command: String, executionResult: ExecutionResult) extends Exception {
  override def toString = ExecutionResult.error(command, executionResult) + s" on server $server"
}

//  @tagless trait SSH {
//    def launch(server: SSHServer, s: String): FS[Unit]
//    def execute(server: SSHServer, s: String): FS[ExecutionResult]
//    def sftp[T](server: SSHServer, f: SFTPClient ⇒ util.Try[T]): FS[T]
//    def readFile[T](server: SSHServer, path: String, f: java.io.InputStream ⇒ T): FS[T]
//    def writeFile(server: SSHServer, is: () ⇒ java.io.InputStream, path: String): FS[Unit]
//    def wrongReturnCode(server: String, command: String, executionResult: ExecutionResult): FS[Unit]
//  }

object SSHServer {

  def apply[A: SSHAuthentication](
    host: String,
    port: Int = 22,
    timeout: Time = 1 minutes,
    keepAlive: Option[Time] = Some(10 seconds),
    sshProxy: Option[SSHServer] = None)(authentication: A): SSHServer = {
    val sSHAuthentication = implicitly[SSHAuthentication[A]]
    new SSHServer(
      login = sSHAuthentication.login(authentication),
      host = host,
      port = port)(
      timeout = timeout,
      authenticate = (sshClient: SSHClient) ⇒ sSHAuthentication.authenticate(authentication, sshClient),
      keepAlive = keepAlive,
      sshProxy = sshProxy)
  }
}

case class SSHServer(
  login: String,
  host: String,
  port: Int
)(
  val timeout: Time,
  val authenticate: SSHClient ⇒ Unit,
  val keepAlive: Option[Time],
  val sshProxy: Option[SSHServer]
) {
  override def toString = s"ssh server $host:$port"
}

case class JobId(jobId: String, workDirectory: String)

/* ----------------------- Job management --------------------- */

def submit(server: SSHServer, description: SSHJobDescription)(using ssh: SSH) =
  val (command, jobId) = SSHJobDescription.jobScript(server, description)
  ssh.launch(server, command)
  JobId(jobId, description.workDirectory)

def run(server: SSHServer, command: String, verbose: Boolean = false)(using ssh: SSH) =
  ssh.execute(server, SSHJobDescription.commandLine(server, command, verbose))

def stdOut(server: SSHServer, jobId: JobId)(using ssh: SSH) =
  readFile(
    server,
    SSHJobDescription.outFile(jobId.workDirectory, jobId.jobId),
    scala.io.Source.fromInputStream(_).mkString)

def stdErr(server: SSHServer, jobId: JobId)(using ssh: SSH) =
  readFile(
    server,
    SSHJobDescription.errFile(jobId.workDirectory, jobId.jobId),
    scala.io.Source.fromInputStream(_).mkString)

def state(server: SSHServer, jobId: JobId)(using ssh: SSH) =
  SSHJobDescription.jobIsRunning(server, jobId) match {
    case true ⇒ JobState.Running: JobState
    case false ⇒
      exists(server, SSHJobDescription.endCodeFile(jobId.workDirectory, jobId.jobId)) match {
        case true ⇒
          // FIXME Limit the size of the read
          val content = ssh.readFile(
            server,
            SSHJobDescription.endCodeFile(jobId.workDirectory, jobId.jobId),
            is ⇒ scala.io.Source.fromInputStream(is).mkString)
          val exitCode = content.takeWhile(_.isDigit).toInt
          SSHJobDescription.translateState(exitCode)
        case false ⇒ JobState.Failed: JobState
      }
  }

def clean(server: SSHServer, job: JobId)(using ssh: SSH) =
  val kill = s"pid=`cat ${SSHJobDescription.pidFile(job.workDirectory, job.jobId)}` ; kill -9 $$pid `ps --ppid $$pid -o pid=`"
  val rm = s"rm -rf ${job.workDirectory}/${job.jobId}*"

  val k = ssh.execute(server, kill)
  ssh.execute(server, rm)
  k.returnCode match {
    case 0 | 1 ⇒ ()
    case _     ⇒ ssh.wrongReturnCode(server.toString, kill, k)
  }

case class SSHJobDescription(command: String, workDirectory: String, timeout: Option[Time] = None)

object SSHJobDescription {

  def jobIsRunning(server: SSHServer, job: JobId)(using ssh: SSH) =
    val cde = s"kill -0 `cat ${pidFile(job.workDirectory, job.jobId)}`"
    val r = ssh.execute(server, cde)
    (r.returnCode == 0)

  def translateState(retCode: Int): JobState =
    retCode match
      case 0 ⇒ JobState.Done
      case _ ⇒ JobState.Failed

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

  def jobScript(server: SSHServer, description: SSHJobDescription)(using ssh: SSH) =
    def script(jobId: String) = {
      def jobCommand =
        description.timeout match {
          case None => description.command
          case Some(t) => s"""timeout --signal=KILL ${t.toSeconds.toInt}s ${description.command}"""
        }

      s"""
         |mkdir -p ${description.workDirectory}
         |cd ${description.workDirectory}
         |${BashShell.source}
         |
         |{
         |  ${jobCommand} >${outFile(description.workDirectory, jobId)} 2>${errFile(description.workDirectory, jobId)}
         |  echo $$? >${endCodeFile(description.workDirectory, jobId)}
         |} & pid=$$!
         |
         |echo $$pid >${pidFile(description.workDirectory, jobId)}
         |
         |""".stripMargin
    }

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

    val jobId = UUID.randomUUID().toString
    val file = scriptFile(description.workDirectory, jobId)
    writeFile(server, () ⇒ new ByteArrayInputStream(script(jobId).getBytes), file)
    (s"""/bin/bash $file""", jobId)
}

/* ------------------------ sftp ---------------------------- */

object FilePermission {

  sealed abstract class FilePermission
  case object USR_RWX extends FilePermission
  case object GRP_RWX extends FilePermission
  case object OTH_RWX extends FilePermission

  def toMask(fps: Set[FilePermission]): Int =

    import net.schmizz.sshj.xfer.{ FilePermission ⇒ SSHJFilePermission }

    import collection.JavaConverters._

    SSHJFilePermission.toMask(
      fps.map:
        case USR_RWX ⇒ SSHJFilePermission.USR_RWX
        case GRP_RWX ⇒ SSHJFilePermission.GRP_RWX
        case OTH_RWX ⇒ SSHJFilePermission.OTH_RWX
      .asJava
    )
}

//  def writeBytes[M[_]](bytes: Array[Byte], path: String)(implicit local: Local[M]) = local.writeBytes(bytes, path)
//  def writeFile[M[_]](file: File, path: String, streamModifier: Option[OutputStream ⇒ OutputStream] = None)(implicit local: Local[M]) = local.writeFile(file, path, streamModifier)
//  def readFile[M[_], T](path: String, f: java.io.InputStream ⇒ T)(implicit local: Local[M]) = local.readFile(path, f)

def readFile[T](server: SSHServer, path: String, f: java.io.InputStream ⇒ T)(using ssh: SSH) = ssh.readFile(server, path, f)
def writeFile(server: SSHServer, is: () ⇒ java.io.InputStream, path: String)(using ssh: SSH): Unit = ssh.writeFile(server, is, path)

def home(server: SSHServer)(using ssh: SSH) = ssh.withSFTP(server, _.canonicalize(".").get)
def exists(server: SSHServer, path: String)(using ssh: SSH) = ssh.withSFTP(server, _.exists(path).get)
def chmod(server: SSHServer, path: String, perms: FilePermission.FilePermission*)(using ssh: SSH) =
  ssh.withSFTP(server, _.chmod(path, FilePermission.toMask(perms.toSet[FilePermission.FilePermission])))

def list(server: SSHServer, path: String)(using ssh: SSH) = ssh.withSFTP(server, _.ls(path)(e ⇒ e != "." || e != "..")).get

def makeDir(server: SSHServer, path: String)(using ssh: SSH) = ssh.withSFTP(server, _.mkdir(path))

def rmDir(server: SSHServer, path: String)(using ssh: SSH): Unit = {
  def remove(entry: ListEntry): Unit =
    import FileType._
    val child = path + "/" + entry.name
    entry.`type` match
      case File      ⇒ rmFile(server, child)
      case Link      ⇒ rmFile(server, child)
      case Directory ⇒ rmDir(server, child)
      case Unknown   ⇒ rmFile(server, child)

  list(server, path).foreach(remove)
  ssh.withSFTP(server, _.rmdir(path))
}

def rmFile(server: SSHServer, path: String)(using ssh: SSH) = ssh.withSFTP(server, _.rm(path)).get
def mv(server: SSHServer, from: String, to: String)(using ssh: SSH) = ssh.withSFTP(server, _.rename(from, to)).get



