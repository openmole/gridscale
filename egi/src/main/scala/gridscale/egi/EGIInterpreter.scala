package gridscale.egi

import freedsl.errorhandler._
import freedsl.filesystem.FileSystemInterpreter
import gridscale.http.HTTPInterpreter

object EGIInterpreter {
  class Interpreters {
    implicit val errorHandlerInterpreter = ErrorHandlerInterpreter()
    implicit val httpInterpreter = HTTPInterpreter()
    implicit val fileSystemInterpreter = FileSystemInterpreter()
    implicit val bdiintepreter = BDIIIntepreter()
  }

  def apply[T](f: Interpreters ⇒ T) = {
    val intp = new Interpreters
    f(intp)
  }
}