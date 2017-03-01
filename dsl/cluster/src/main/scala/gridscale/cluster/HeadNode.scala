package gridscale.cluster

import java.io.ByteArrayInputStream

import scala.language.higherKinds

import gridscale._
import ssh._

object HeadNode {

  implicit def sshHeadNode[M[_]](implicit sshM: SSH[M]) = new HeadNode[SSHServer, M] {
    override def execute(server: SSHServer, cmd: String) = sshM.execute(server, cmd)
    override def write(server: SSHServer, bytes: Array[Byte], path: String) = sshM.sftp(server, _.writeFile(new ByteArrayInputStream(bytes), path))
    override def read(server: SSHServer, path: String) = sshM.readFile(server, path, io.Source.fromInputStream(_).mkString)
    override def rm(server: SSHServer, path: String): M[Unit] = sshM.sftp(server, _.rm(path))
  }

}

trait HeadNode[S, M[_]] {
  def execute(server: S, cmd: String): M[ExecutionResult]
  def write(server: S, bytes: Array[Byte], path: String): M[Unit]
  def read(server: S, path: String): M[String]
  def rm(server: S, path: String): M[Unit]
}
