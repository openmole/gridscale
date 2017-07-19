package gridscale.http

object TestHTTP extends App {

  import freedsl.dsl._

  println(get("https://www.openmole.org"))

}
