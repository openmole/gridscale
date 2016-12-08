package fr.iscpif.gridscale.ssh
import java.io.File

import fr.iscpif.gridscale.authentication._
import concurrent.duration._

object TestSSH extends App {

  import freek._
  import cats.implicits._
  import freedsl.random._
  import freedsl.util._

  val c = freedsl.dsl.merge(Util, SSH, Random)
  import c._

  def randomData[M[_]](implicit randomM: Random[M]) = randomM.shuffle(Seq(1, 2, 2, 3, 3, 3))

  def job(data: String) =
    SSHJobDescription(
      command = s"echo -n $data",
      workDirectory = "/tmp/")

  val prg =
    for {
      sData ← randomData[M]
      jobId ← submit[M](job(sData.mkString(", ")))
      _ ← implicitly[Util[M]].sleep(2 second)
      s ← state[M](jobId)
      out ← stdOut[M](jobId)
    } yield s"""Job status is $s, stdout is "$out"."""

  val zebulon = Server("zebulon.iscpif.fr")
  val key = PrivateKey("reuillon", new File("/home/reuillon/.ssh/id_rsa"), "")

  val sshClient =
    SSH.client(
      zebulon,
      _.authPrivateKey(key)
    )

  val interpreter =
    SSH.interpreter(sshClient) :&:
      Util.interpreter :&:
      Random.interpreter(42)

  try println(result.getOption(prg.value.interpret(interpreter))) //.map(_.leftMap(_.asInstanceOf[SSH.ConnectionError].t.printStackTrace)))
  finally sshClient.foreach(_.close())
}
