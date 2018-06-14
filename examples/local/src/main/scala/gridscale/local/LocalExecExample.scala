package gridscale.local

import effectaside._
import gridscale.cluster.LocalCluster

object LocalExecExample extends App {

  val headNode = LocalHost()

  val jobDescription = "cd /tmp && cat /etc/passwd"

  def res(implicit system: Effect[System], local: Effect[Local]) = {
    execute(jobDescription)
  }

  LocalCluster { intp ⇒
    import intp._
    println(res)
  }

}
