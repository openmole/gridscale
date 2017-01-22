package gridscale.cluster

import java.io.ByteArrayInputStream

import gridscale._
import ssh._

object HeadNode {

  implicit def sshHeadNode[M[_]](implicit sshM: SSH[M]) = new HeadNode[SSHServer[_], M] {
    override def execute(server: SSHServer[_], cmd: String) = sshM.execute(server, cmd)
    override def write(server: SSHServer[_], bytes: Array[Byte], path: String) = sshM.sftp(server, _.writeFile(new ByteArrayInputStream(bytes), path))
    override def read(server: SSHServer[_], path: String) = sshM.readFile(server, path, io.Source.fromInputStream(_).mkString)
    override def rm(server: SSHServer[_], path: String): M[Unit] = sshM.sftp(server, _.rm(path))
  }

}

trait HeadNode[S, M[_]] {
  def execute(server: S, cmd: String): M[ExecutionResult]
  def write(server: S, bytes: Array[Byte], path: String): M[Unit]
  def read(server: S, path: String): M[String]
  def rm(server: S, path: String): M[Unit]
}
