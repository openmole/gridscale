package gridscale.http

object TestHTTP extends App {

  import freedsl.dsl._

  val server =
    HTTPSServer(
      "https://ccdirac05.in2p3.fr/",
      HTTPS.socketFactory("/gridscale/http/dirac-truststore", "emptypassword")
    )

  val intp = merge(HTTP.interpreter)
  import intp.implicits._

  println(intp.run(read[intp.M](server, "")))

}
