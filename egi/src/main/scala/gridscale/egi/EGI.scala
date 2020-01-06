package gridscale.egi

import gridscale.effectaside._
import gridscale.http._

object EGI {
  class Interpreters {
    implicit val http: Effect[HTTP] = HTTP()
    implicit val fileSystem: Effect[FileSystem] = FileSystem()
    implicit val bdii: Effect[BDII] = BDII()
    implicit val system: Effect[System] = System()
  }

  def apply() = new Interpreters

  def apply[T](f: Interpreters ⇒ T) = {
    val intp = new Interpreters
    f(intp)
  }
}