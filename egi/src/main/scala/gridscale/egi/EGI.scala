package gridscale.egi

import effectaside._
import gridscale.http._

object EGI {
  class Interpreters(proxy: Option[HTTPProxy] = None) {
    implicit val http: Effect[HTTP] = HTTP(proxy)
    implicit val fileSystem: Effect[FileSystem] = FileSystem()
    implicit val bdii: Effect[BDII] = BDII()
    implicit val system: Effect[System] = System()
  }

  def apply(proxy: Option[HTTPProxy] = None) = new Interpreters(proxy)

  def apply[T](f: Interpreters ⇒ T)(proxy: Option[HTTPProxy]) = {
    val intp = new Interpreters(proxy)
    f(intp)
  }
}