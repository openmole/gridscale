package gridscale.cluster

import java.io.ByteArrayInputStream

import gridscale._
import ssh._
import local._

object HeadNode:
  given (using SSH): Conversion[SSHServer, HeadNode] = SSHHeadNode.apply

sealed trait HeadNode:
  def execute(cmd: String): ExecutionResult
  def write(bytes: Array[Byte], path: String): Unit
  def read(path: String): String
  def rmFile(path: String): Unit

case class SSHHeadNode(server: SSHServer)(using SSH) extends HeadNode:
  override def execute(cmd: String) = gridscale.ssh.run(server, cmd)
  override def write(bytes: Array[Byte], path: String) = ssh.writeFile(server, () ⇒ new ByteArrayInputStream(bytes), path)
  override def read(path: String) = ssh.readFile(server, path, scala.io.Source.fromInputStream(_).mkString)
  override def rmFile(path: String): Unit = ssh.rmFile(server, path)

case class LocalHeadNode() extends HeadNode:
  override def execute(cmd: String) = Local.execute(cmd)
  override def write(bytes: Array[Byte], path: String) = Local.writeBytes(bytes, path)
  override def read(path: String) = Local.readFile(path, scala.io.Source.fromInputStream(_).mkString)
  override def rmFile(path: String): Unit = Local.rmFile(path)
