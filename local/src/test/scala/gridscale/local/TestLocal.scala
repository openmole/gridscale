package gridscale.local

object TestLocal extends App {

  import freedsl.system._
  import freedsl.dsl._

  val c = merge(Local, System)
  import c.implicits._

  val filepath = "/tmp/hello.txt"

  val prg = for {
    _ ← writeFile[c.M]("Hello, world".getBytes, filepath)
    out ← readFile[c.M](filepath)
    _ ← writeFile[c.M]((out + " again !!!").getBytes, filepath + "2")
    _ ← rm[c.M](filepath)
    res ← execute[c.M]("hostname")
  } yield s"""Initial stdout was "$res"""

  val interpreter = merge(Local.interpreter, System.interpreter)

  println(interpreter.run(prg))

}
