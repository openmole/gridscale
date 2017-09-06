package gridscale.egi

import java.io.ByteArrayInputStream

import cats.implicits._
import freedsl.errorhandler._
import freedsl.filesystem._
import freedsl.tool._
import gridscale.webdav._
import freedsl.dsl._

object WedDAVExample extends App {

  val password = scala.io.Source.fromFile("/home/reuillon/.globus/password").getLines().next().trim
  val p12 = P12Authentication(new java.io.File("/home/reuillon/.globus/certificate.p12"), password)
  val certificateDirectory = new java.io.File("/home/reuillon/.openmole/simplet/persistent/CACertificates/")
  val bdii = BDIIServer("topbdii.grif.fr", 2170)

  EGIInterpreter { impl ⇒
    import impl._
    val prg = for {
      proxy ← VOMS.proxy[DSL]("voms.hellasgrid.gr:15160", p12, certificateDirectory)
      lal ← BDII[DSL].webDAVs(bdii, "vo.complex-systems.eu").map(_.find(_.contains("lal")).get)
      webdav = WebDAVSServer(lal, proxy.factory)
      _ ← exists[DSL](webdav, "youpi2.txt").ifM(rmFile[DSL](webdav, "youpi2.txt"), noop[DSL])
      _ ← writeStream[DSL](webdav, "youpi2.txt", () ⇒ new ByteArrayInputStream("youpi doky".getBytes))
      c ← read[DSL](webdav, "youpi2.txt")
    } yield c

    println(prg.eval)

  }
}