package gridscale.cluster

import gridscale.ssh._
import gridscale.local._
import gridscale.effectaside._

object SSHCluster {
  class Interpreters {
    implicit val systemEffect: Effect[System] = System()
    implicit val sshEffect: Effect[SSH] = SSH(SSHCache())
  }

  def apply[T](f: Interpreters ⇒ T) = {
    val intp = new Interpreters
    try f(intp)
    finally intp.sshEffect().close()
  }
}

object LocalCluster {
  class Interpreters {
    implicit val system: Effect[System] = System()
    implicit val local: Effect[Local] = Local()
  }

  def apply[T](f: Interpreters ⇒ T) = {
    val intp = new Interpreters
    f(intp)
  }
}

object ClusterInterpreter {
  class Interpreters {
    implicit val system: Effect[System] = System()
    implicit val ssh: Effect[SSH] = SSH(SSHCache())
    implicit val local: Effect[Local] = Local()
  }

  def apply[T](f: Interpreters ⇒ T) = {
    val intp = new Interpreters
    try f(intp)
    finally intp.ssh().close()
  }
}