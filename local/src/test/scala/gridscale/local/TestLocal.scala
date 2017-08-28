package gridscale.local

object TestLocal extends App {

  import cats._
  import cats.implicits._
  import freedsl.system._

  val filepath = "/tmp/hello.txt"

  def prg[M[_]: System: Local: Monad] = for {
    _ ← writeFile[M]("Hello, world".getBytes, filepath)
    out ← readFile[M](filepath)
    _ ← writeFile[M]((out + " again !!!").getBytes, filepath + "2")
    _ ← rmFile[M](filepath)
    res ← execute[M]("hostname")
  } yield s"""Stdout: ${res.stdOut}"""

  implicit val systemInterpreter = new SystemInterpreter
  implicit val localIntepreter = new LocalInterpreter

  println(prg[util.Try])

}
