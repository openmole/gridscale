package gridscale.dirac

import gridscale._
import gridscale.authentication._
import gridscale.http._

object TestDIRAC extends App:

  val password = scala.io.Source.fromFile("/home/reuillon/.globus/password").getLines().next().trim
  val p12 = P12Authentication(new java.io.File("/home/reuillon/.globus/geant.p12"), password)
  val certificateDirectory = new java.io.File("/home/reuillon/.openmole/simplet/persistent/CACertificates/")

  val description = JobDescription("/bin/uname", "-a", stdOut = Some("output"), outputSandbox = Seq("output"))

  def prg(using HTTP) =
    val service = getService("vo.complex-systems.eu", certificateDirectory)
    //val service = Service("https://ccdiracli08.in2p3.fr:9178", "complex_user")
    val s = server(service, p12, certificateDirectory)
    val t = token(s)
    delegate(s, p12, t)
    val j = submit(s, description, t) //.repeat(10)
    //gs ← queryGroupState[M](s, t, "testgroup")
    val st = waitUntilEnded { () ⇒  println(j) ; state(s, t, j) }
    downloadOutputSandbox(s, t, j, java.io.File("/tmp/output"))
    delete(s, t, j)
    j

  HTTP.withHTTP:
    println(prg)

