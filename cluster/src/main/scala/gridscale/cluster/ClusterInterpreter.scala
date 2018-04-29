package gridscale.cluster

import gridscale.ssh._
import gridscale.local._
import effectaside._

object SSHCluster {
  class Interpreters {
    implicit val system = System()
    implicit val ssh = SSH(SSHCache())
  }

  def apply[T](f: Interpreters ⇒ T) = {
    val intp = new Interpreters
    try f(intp)
    finally intp.ssh().close()
  }
}

object LocalCluster {
  class Interpreters {
    implicit val system = System()
    implicit val local = Local()
  }

  def apply[T](f: Interpreters ⇒ T) = {
    val intp = new Interpreters
    f(intp)
  }
}

object ClusterInterpreter {
  class Interpreters {
    implicit val system = System()
    implicit val ssh = SSH(SSHCache())
    implicit val local = Local()
  }

  def apply[T](f: Interpreters ⇒ T) = {
    val intp = new Interpreters
    try f(intp)
    finally intp.ssh().close()
  }
}