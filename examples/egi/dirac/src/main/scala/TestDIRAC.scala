package gridscale.dirac

import effectaside._
import gridscale._
import gridscale.authentication._
import gridscale.http._

object TestDIRAC extends App {

  val password = scala.io.Source.fromFile("/home/reuillon/.globus/password").getLines().next().trim
  val p12 = P12Authentication(new java.io.File("/home/reuillon/.globus/certificate.p12"), password)
  val certificateDirectory = new java.io.File("/home/reuillon/.openmole/simplet/persistent/CACertificates/")

  val description = JobDescription("/bin/uname", "-a", stdOut = Some("output"), outputSandbox = Seq("output"))

  def prg(implicit http: Effect[HTTP], fileSystem: Effect[FileSystem], system: Effect[System]) = {
    val service = getService("vo.complex-systems.eu")
    val s = server(service, p12, certificateDirectory)
    val t = token(s)
    val j = submit(s, description, t) //.repeat(10)
    //gs ← queryGroupState[M](s, t, "testgroup")
    val st = waitUntilEnded(() ⇒ state(s, t, j))
    downloadOutputSandbox(s, t, j, "/tmp/output")
    delete(s, t, j)
    j
  }

  DIRAC { interpreters ⇒
    import interpreters._
    println(prg)
  }

}
