package gridscale.egi

import freedsl.errorhandler._
import freedsl.filesystem.FileSystemInterpreter
import freedsl.system.SystemInterpreter
import gridscale.http.HTTPInterpreter

object EGIInterpreter {
  class Interpreters {
    implicit val errorHandlerInterpreter = ErrorHandlerInterpreter()
    implicit val httpInterpreter = HTTPInterpreter()
    implicit val fileSystemInterpreter = FileSystemInterpreter()
    implicit val bdiintepreter = BDIIIntepreter()
    implicit val systemInterpreter = SystemInterpreter()
  }

  def apply() = new Interpreters

  def apply[T](f: Interpreters ⇒ T) = {
    val intp = new Interpreters
    f(intp)
  }
}