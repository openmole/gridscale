package gridscale.cluster

import java.io.ByteArrayInputStream

import scala.language.higherKinds

import gridscale._
import ssh._
import local._

object HeadNode {

  implicit def sshHeadNode[M[_]: cats.Monad](implicit sshM: SSH[M]) = new HeadNode[SSHServer, M] {
    override def execute(server: SSHServer, cmd: String) = gridscale.ssh.run[M](server, cmd)
    override def write(server: SSHServer, bytes: Array[Byte], path: String) = ssh.writeFile(server, () ⇒ new ByteArrayInputStream(bytes), path)
    override def read(server: SSHServer, path: String) = ssh.readFile(server, path, io.Source.fromInputStream(_).mkString)
    override def rmFile(server: SSHServer, path: String): M[Unit] = ssh.rmFile(server, path)
  }

  implicit def localHeadNode[M[_]](implicit localM: Local[M]) = new HeadNode[LocalHost, M] {
    override def execute(server: LocalHost, cmd: String) = localM.execute(cmd)
    override def write(server: LocalHost, bytes: Array[Byte], path: String) = local.writeBytes(bytes, path)
    override def read(server: LocalHost, path: String) = local.readFile(path, io.Source.fromInputStream(_).mkString)
    override def rmFile(server: LocalHost, path: String): M[Unit] = localM.rmFile(path)
  }

}

trait HeadNode[S, M[_]] {
  def execute(server: S, cmd: String): M[ExecutionResult]
  def write(server: S, bytes: Array[Byte], path: String): M[Unit]
  def read(server: S, path: String): M[String]
  def rmFile(server: S, path: String): M[Unit]
}
