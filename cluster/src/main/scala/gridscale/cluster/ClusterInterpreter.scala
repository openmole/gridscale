package gridscale.cluster

import freedsl.errorhandler.{ ErrorHandler, ErrorHandlerInterpreter }
import freedsl.system.{ System, SystemInterpreter }
import gridscale.ssh._
import gridscale.local._
import freestyle.module

object SSHClusterInterpreter {
  class Interpreters {
    implicit val systemInterpreter = SystemInterpreter()
    implicit val errorHandlerInterpreter = ErrorHandlerInterpreter()
    implicit val sshInterpreter = SSHInterpreter()
  }

  def apply[T](f: Interpreters ⇒ T) = {
    val intp = new Interpreters
    try f(intp)
    finally intp.sshInterpreter.close()
  }
}

object LocalClusterInterpreter {
  class Interpreters {
    implicit val systemInterpreter = SystemInterpreter()
    implicit val errorHandlerInterpreter = ErrorHandlerInterpreter()
    implicit val localIntepreter = LocalInterpreter()
  }

  def apply[T](f: Interpreters ⇒ T) = {
    val intp = new Interpreters
    f(intp)
  }
}

object ClusterInterpreter {
  class Interpreters {
    implicit val systemInterpreter = SystemInterpreter()
    implicit val sshInterpreter = SSHInterpreter()
    implicit val errorHandlerInterpreter = ErrorHandlerInterpreter()
    implicit val localIntepreter = LocalInterpreter()
  }

  def apply[T](f: Interpreters ⇒ T) = {
    val intp = new Interpreters
    try f(intp)
    finally intp.sshInterpreter.close()
  }
}