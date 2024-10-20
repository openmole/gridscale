package gridscale.egi

import java.io.ByteArrayInputStream
import gridscale.egi.*
import gridscale.webdav.*
import gridscale.authentication.*
import gridscale.http.HTTP

@main def wedDAVExample =
  val password = scala.io.Source.fromFile("/home/reuillon/.globus/password").getLines().next().trim
  val p12 = P12Authentication(new java.io.File("/home/reuillon/.globus/geant.p12"), password)
  val certificateDirectory = new java.io.File("/home/reuillon/.openmole/simplet/persistent/CACertificates/")
  val bdiiServer = BDIIServer("topbdii.grif.fr", 2170)


  HTTP.withHTTP:
    val proxy = VOMS.proxy("voms2.hellasgrid.gr:15160", p12, certificateDirectory)

    println(BDII.webDAVs(bdiiServer, "vo.complex-systems.eu"))

    //val webdavInfo = bdii().webDAVs(bdiiServer, "vo.complex-systems.eu").find(_.contains("lal")).get
    val webdavInfo = "https://eos.grif.fr:11000/eos/grif/complex/"
    val webdav = WebDAVSServer(webdavInfo, proxy.factory)

    println(list(webdav, "/"))

    for
      i <- 0 to 10000
    do
      if (exists(webdav, "youpi2.txt")) rmFile(webdav, "youpi2.txt")

      writeStream(webdav, () ⇒ new ByteArrayInputStream("youpi doky".getBytes), "youpi2.txt")
      val c = read(webdav, "youpi2.txt")

      println(c)

