package gridscale.iexec

import effectaside._
import gridscale._
import gridscale.cluster._
import gridscale.iexec._
import gridscale.local._

object IexecExample extends App {

  val headNode = LocalHost()

  val jobDescription = IEXECJobDescription(workDirectory = "/tmp/iexec-init",
    IexecFilesPath="", //Path to iexec binary files
    dappAddress = "0xd2b9d3ecc76b6d43277fd986afdb8b79685d4d1a",
    arguments = "5",
    dappCost = 1)

  def res(implicit system: Effect[System], ssh: Effect[Local]) = {
    impl.populateIexecAccount(headNode, jobDescription, 1)

    val job = submit(headNode, jobDescription)
    val s = waitUntilEnded(() ⇒ state(headNode, job, jobDescription))
    val out = stdOut(headNode, job)
    clean(headNode, job)

    (s, out)
  }

  LocalCluster { intp ⇒
    import intp._
    println(res)
  }

}
