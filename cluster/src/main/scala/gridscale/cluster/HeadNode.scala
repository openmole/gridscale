package gridscale.cluster

import java.io.ByteArrayInputStream

import scala.language.higherKinds
import gridscale.effectaside._
import gridscale._
import ssh._
import local._

object HeadNode {

  implicit def sshHeadNode(implicit sshM: Effect[SSH]): HeadNode[SSHServer] = new HeadNode[SSHServer] {
    override def execute(server: SSHServer, cmd: String) = gridscale.ssh.run(server, cmd)
    override def write(server: SSHServer, bytes: Array[Byte], path: String) = ssh.writeFile(server, () ⇒ new ByteArrayInputStream(bytes), path)
    override def read(server: SSHServer, path: String) = ssh.readFile(server, path, scala.io.Source.fromInputStream(_).mkString)
    override def rmFile(server: SSHServer, path: String): Unit = ssh.rmFile(server, path)
  }

  implicit def localHeadNode(implicit local: Effect[Local]): HeadNode[LocalHost] = new HeadNode[LocalHost] {
    override def execute(server: LocalHost, cmd: String) = local().execute(cmd)
    override def write(server: LocalHost, bytes: Array[Byte], path: String) = local().writeBytes(bytes, path)
    override def read(server: LocalHost, path: String) = local().readFile(path, scala.io.Source.fromInputStream(_).mkString)
    override def rmFile(server: LocalHost, path: String): Unit = local().rmFile(path)
  }

}

trait HeadNode[S] {
  def execute(server: S, cmd: String): ExecutionResult
  def write(server: S, bytes: Array[Byte], path: String): Unit
  def read(server: S, path: String): String
  def rmFile(server: S, path: String): Unit
}
