package gridscale.http

object TestHTTP extends App {

  import freedsl.dsl._

  val server = Server("http://zebulon.iscpif.fr/")
  val intp = merge(HTTP.interpreter(server))
  import intp.implicits._
  println(
    intp.run(list[intp.M]("~reuillon/"))
  )

}
