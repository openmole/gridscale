package gridscale.ssh

import java.io.ByteArrayInputStream
import gridscale.*
import gridscale.tools.*
import gridscale.tools.shell.BashShell

import scala.language.{higherKinds, postfixOps}
import gridscale.ssh.sshj.{SFTPClient, SSHClient}
import gridscale.authentication.*
import squants.*
import time.TimeConversions.*
import gridscale.tools.cache.*

import java.util.UUID
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import java.util.concurrent.locks.{Lock, ReentrantLock}

object SSHAuthentication:

  given SSHAuthentication[UserPassword] with
    override def login(t: UserPassword) = t.user
    override def authenticate(t: UserPassword, sshClient: SSHClient): Unit =
      sshClient.authPassword(t.user, t.password)

  given SSHAuthentication[PrivateKey] with
    override def login(t: PrivateKey) = t.user
    override def authenticate(t: PrivateKey, sshClient: SSHClient): Unit =
      sshClient.authPrivateKey(t)


trait SSHAuthentication[T]:
  def login(t: T): String
  def authenticate(t: T, sshClient: SSHClient): Unit

object SSH:

  def withSSH[T](server: SSHServer, reconnect: Option[Time] = None)(f: SSH ?=> T): T =
    val ssh = SSH(server, reconnect)
    try f(using ssh)
    finally ssh.close()

  def apply(server: SSHServer, reconnect: Option[Time] = None): SSH =
    val clientCache: ConnectionCache =
      reconnect match
        case None => ConnectionCache.FixedConnection(() => SSH.client(server))
        case Some(t) => ConnectionCache.Reconnect(() => SSH.client(server), t)

    new SSH(server, clientCache)

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
      try
        val jumpClient =
          server.proxy.map: proxy =>
            val client = SSHClient(
                host = proxy.host,
                port = proxy.port,
                timeout = proxy.timeout,
                keepAlive = proxy.keepAlive,
                proxyJump =  None)

            SSHClient.connect(client)
            authenticate(proxy, client)

        val ssh = SSHClient(host = server.host, port = server.port, timeout = server.timeout, keepAlive = server.keepAlive, proxyJump = jumpClient)
        SSHClient.connect(ssh)
      catch
        case t: Throwable ⇒ throw ConnectionError(s"Error connecting to $server", t)

    authenticate(server, ssh)


  object ConnectionCache:
    def use[T](cache: ConnectionCache)(f: SSHClient => T): T =
      cache match
        case c: FixedConnection => FixedConnection.use(c)(f)
        case c: Reconnect => Reconnect.use(c)(f)

    def close(cache: ConnectionCache) =
      cache match
        case c: FixedConnection => FixedConnection.close(c)
        case c: Reconnect => Reconnect.close(c)

    object FixedConnection:
      def apply(connect: () => SSHClient) =
        new FixedConnection(connect, connect())

      def use[T](c: FixedConnection)(f: SSHClient => T): T =
        val client =
          c.synchronized:
            if !c.client.isConnected
            then
              util.Try(c.client.close())
              c.client = c.connect()

            c.client

        f(client)

      def close(c: FixedConnection) = c.client.close()


    case class FixedConnection(connect: () => SSHClient, var client: SSHClient) extends ConnectionCache

    object Reconnect:
      def apply(connect: () => SSHClient, interval: Time) =
        new Reconnect(connect, interval, Connection(Lazy(connect())))

      case class Connection(client: Lazy[SSHClient], created: Long = System.currentTimeMillis(), var used: Int = 0)

      def close(r: Reconnect) = r.connection.client().close()

      def use[T](r: Reconnect)(f: SSHClient => T): T =
        def expired(c: Connection, duration: Time): Boolean =
          (c.created + duration.millis) < System.currentTimeMillis()

        val (connection, old) =
          r.synchronized:
            val current = r.connection
            if expired(current, r.duration)
            then
              val newConnection = Connection(Lazy(r.connect()))
              r.connection = newConnection
              newConnection.used += 1
              (newConnection, Some(current))
            else
              current.used += 1
              (current, None)

        try f(connection.client())
        finally
          r.synchronized(connection.used -= 1)
          old.foreach: old =>
            if r.synchronized(old.used == 0)
            then old.client().close()


    case class Reconnect(connect: () => SSHClient, duration: Time, var connection: Reconnect.Connection) extends ConnectionCache

  sealed trait ConnectionCache

  object SSHCache:
    def withCache[T](cache: ConnectionCache)(f: SSHClient ⇒ T): T = ConnectionCache.use(cache)(f)
    def close(cache: ConnectionCache) = ConnectionCache.close(cache)

class SSH(val server: SSHServer, clientCache: SSH.ConnectionCache) extends AutoCloseable:

  import SSH.*

  def execute(s: String) =
    SSHCache.withCache(clientCache): c =>
      try SSHClient.run(c, s).get
      catch
        case t: Throwable ⇒ throw ExecutionError(s"Error executing $s on $server", t)

  def launch(s: String) =
    SSHCache.withCache(clientCache): c =>
      SSHClient.launchInBackground(c, s)

  def withSFTP[T](f: SFTPClient ⇒ T): T =
    SSHCache.withCache(clientCache): c =>
      try SSHClient.withSFTP(c, f)
      catch
        case t: Throwable ⇒ throw SFTPError(s"Error in sftp transfer on $server", t)

  def readFile[T](path: String, f: java.io.InputStream ⇒ T) =
    SSHCache.withCache(clientCache): c =>
      try SSHClient.withSFTP(c, s ⇒ s.readAheadFileInputStream(path).map(f)).get
      catch
        case t: SFTPError => throw t
        case t: Throwable ⇒ throw SFTPError(s"Error while reading $path via sftp on $server", t)

  def writeFile(is: () ⇒ java.io.InputStream, path: String) =
    def write(client: SSHClient) =
      val ois = is()
      try SSHClient.withSFTP(client, _.writeFile(ois, path)).get
      finally ois.close()

    SSHCache.withCache(clientCache): c =>
      try write(c)
      catch
        case t: SFTPError => throw t
        case t: Throwable ⇒ throw SFTPError(s"Error while writing to $path via sftp on $server", t)

  def wrongReturnCode(server: String, command: String, executionResult: ExecutionResult) = throw ReturnCodeError(server, command, executionResult)

  def close(): Unit =
    SSHCache.close(clientCache)


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

object SSHServer:

  def apply[A: SSHAuthentication](
    host: String,
    port: Int = 22,
    timeout: Time = 1 minutes,
    keepAlive: Option[Time] = Some(10 seconds),
    sshProxy: Option[SSHServer] = None)(authentication: A): SSHServer =
    val sSHAuthentication = implicitly[SSHAuthentication[A]]
    new SSHServer(
      login = sSHAuthentication.login(authentication),
      host = host,
      port = port)(
      timeout = timeout,
      authenticate = (sshClient: SSHClient) ⇒ sSHAuthentication.authenticate(authentication, sshClient),
      keepAlive = keepAlive,
      proxy = sshProxy)


case class SSHServer(
  login: String,
  host: String,
  port: Int
)(
  val timeout: Time,
  val authenticate: SSHClient ⇒ Unit,
  val keepAlive: Option[Time],
  val proxy: Option[SSHServer]
):
  override def toString = s"ssh server $host:$port"

case class JobId(jobId: String, workDirectory: String)

/* ----------------------- Job management --------------------- */

def submit(description: SSHJobDescription)(using ssh: SSH) =
  val (command, jobId) = SSHJobDescription.jobScript(description)
  ssh.launch(command)
  JobId(jobId, description.workDirectory)

def run(command: String, verbose: Boolean = false)(using ssh: SSH) =
  ssh.execute(SSHJobDescription.commandLine(command, verbose))

def stdOut(jobId: JobId)(using ssh: SSH) =
  readFile(
    SSHJobDescription.outFile(jobId.workDirectory, jobId.jobId),
    scala.io.Source.fromInputStream(_).mkString)

def stdErr(jobId: JobId)(using ssh: SSH) =
  readFile(
    SSHJobDescription.errFile(jobId.workDirectory, jobId.jobId),
    scala.io.Source.fromInputStream(_).mkString)

def state(jobId: JobId)(using ssh: SSH) =
  SSHJobDescription.jobIsRunning(jobId) match
    case true ⇒ JobState.Running: JobState
    case false ⇒
      exists(SSHJobDescription.endCodeFile(jobId.workDirectory, jobId.jobId)) match
        case true ⇒
          // FIXME Limit the size of the read
          val content = ssh.readFile(
            SSHJobDescription.endCodeFile(jobId.workDirectory, jobId.jobId),
            is ⇒ scala.io.Source.fromInputStream(is).mkString)
          val exitCode = content.takeWhile(_.isDigit).toInt
          SSHJobDescription.translateState(exitCode)
        case false ⇒ JobState.Failed: JobState


def clean(job: JobId)(using ssh: SSH) =
  val kill = s"pid=`cat '${SSHJobDescription.pidFile(job.workDirectory, job.jobId)}'` ; kill -9 $$pid `ps --ppid $$pid -o pid=`"
  val rm = s"rm -rf '${job.workDirectory}/${job.jobId}'*"

  val k = ssh.execute(kill)
  ssh.execute(rm)
  k.returnCode match
    case 0 | 1 ⇒ ()
    case _     ⇒ ssh.wrongReturnCode(ssh.server.toString, kill, k)

case class SSHJobDescription(command: String, workDirectory: String, timeout: Option[Time] = None)

object SSHJobDescription:

  def jobIsRunning(job: JobId)(using ssh: SSH) =
    val cde = s"kill -0 `cat '${pidFile(job.workDirectory, job.jobId)}'`"
    val r = ssh.execute(cde)
    r.returnCode == 0

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

  def commandLine(command: String, verbose: Boolean = false) =
    s"""
           |env -i bash ${if verbose then "-x" else ""} <<EOF
           |${BashShell.source}
           |${command}
           |EOF
           |""".stripMargin

  def jobScript(description: SSHJobDescription)(using ssh: SSH) =
    def script(jobId: String) =
      def jobCommand =
        description.timeout match
          case None => description.command
          case Some(t) => s"""timeout --signal=KILL ${t.toSeconds.toInt}s ${description.command}"""

      s"""
         |mkdir -p '${description.workDirectory}'
         |cd '${description.workDirectory}'
         |${BashShell.source}
         |
         |{
         |  ${jobCommand} >'${outFile(description.workDirectory, jobId)}' 2>'${errFile(description.workDirectory, jobId)}'
         |  echo $$? >'${endCodeFile(description.workDirectory, jobId)}'
         |} & pid=$$!
         |
         |echo $$pid >'${pidFile(description.workDirectory, jobId)}'
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

    val jobId = UUID.randomUUID().toString
    val file = scriptFile(description.workDirectory, jobId)
    writeFile(() => new ByteArrayInputStream(script(jobId).getBytes), file)
    (s"""/bin/bash '$file'""", jobId)


/* ------------------------ sftp ---------------------------- */

object FilePermission:

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

//  def writeBytes[M[_]](bytes: Array[Byte], path: String)(implicit local: Local[M]) = local.writeBytes(bytes, path)
//  def writeFile[M[_]](file: File, path: String, streamModifier: Option[OutputStream ⇒ OutputStream] = None)(implicit local: Local[M]) = local.writeFile(file, path, streamModifier)
//  def readFile[M[_], T](path: String, f: java.io.InputStream ⇒ T)(implicit local: Local[M]) = local.readFile(path, f)

def readFile[T](path: String, f: java.io.InputStream ⇒ T)(using ssh: SSH) = ssh.readFile(path, f)
def writeFile(is: () ⇒ java.io.InputStream, path: String)(using ssh: SSH): Unit = ssh.writeFile(is, path)

def home()(using ssh: SSH) = ssh.withSFTP(_.canonicalize(".").get)
def exists(path: String)(using ssh: SSH) = ssh.withSFTP(_.exists(path).get)
def chmod(path: String, perms: FilePermission.FilePermission*)(using ssh: SSH) =
  ssh.withSFTP(_.chmod(path, FilePermission.toMask(perms.toSet[FilePermission.FilePermission])))

def list(path: String)(using ssh: SSH) = ssh.withSFTP(_.ls(path)(e ⇒ e != "." || e != "..")).get

def makeDir(path: String)(using ssh: SSH) = ssh.withSFTP(_.mkdir(path))

def rmDir(path: String)(using ssh: SSH): Unit =
  def remove(entry: ListEntry): Unit =
    import FileType._
    val child = path + "/" + entry.name
    entry.`type` match
      case File      ⇒ rmFile(child)
      case Link      ⇒ rmFile(child)
      case Directory ⇒ rmDir(child)
      case Unknown   ⇒ rmFile(child)

  list(path).foreach(remove)
  ssh.withSFTP(_.rmdir(path))

def rmFile(path: String)(using ssh: SSH) = ssh.withSFTP(_.rm(path)).get
def mv(from: String, to: String)(using ssh: SSH) = ssh.withSFTP(_.rename(from, to)).get



