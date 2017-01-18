package gridscale.http

object TestHTTP extends App {

  import freedsl.dsl._

  val server =
    HTTPSServer(
      "https://ccdirac05.in2p3.fr/",
      HTTPS.keyStoreFactory("/gridscale/http/dirac-truststore", "emptypassword")
    )

  val intp = merge(HTTP.interpreter(server))
  import intp.implicits._
  println(
    intp.run(read[intp.M](""))
  )

}
