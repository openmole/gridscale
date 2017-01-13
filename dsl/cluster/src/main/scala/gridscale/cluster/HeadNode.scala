package gridscale.cluster

import java.io.ByteArrayInputStream

import gridscale._
import ssh._

object HeadNode {

  implicit def sshHeadNode[M[_]](implicit sshM: SSH[M]) = new HeadNode[M] {
    override def execute(cmd: String) = sshM.execute(cmd)
    override def write(bytes: Array[Byte], path: String) = sshM.sftp(_.writeFile(new ByteArrayInputStream(bytes), path))
    override def read(path: String) = sshM.readFile(path, io.Source.fromInputStream(_).mkString)
    override def rm(path: String): M[Unit] = sshM.sftp(_.rm(path))
  }

}

trait HeadNode[M[_]] {
  def execute(cmd: String): M[ExecutionResult]
  def write(bytes: Array[Byte], path: String): M[Unit]
  def read(path: String): M[String]
  def rm(path: String): M[Unit]
}
