/*
 * Copyright (C) 2012 Romain Reuillon
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package fr.iscpif.gridscale

import authentication._
import information._
import storage._
import java.io.File
import jobservice._
import tools._

object GridScale extends App {
  

//  VOMSAuthentication.setCARepository(new File( "/home/reuillon/.openmole/CACertificates"))
//  
//  val auth = new P12VOMSAuthentication {
//    def serverURL = "voms://cclcgvomsli01.in2p3.fr:15000/O=GRID-FR/C=FR/O=CNRS/OU=CC-IN2P3/CN=cclcgvomsli01.in2p3.fr"
//    def voName = "biomed"
//    def proxyFile = new File("/tmp/proxy.x509")
//    def fquan = None
//    def lifeTime = 3600
//    def certificate = new File("/home/reuillon/.globus/certificate.p12")
//  }
//  
//  val bdii = new BDII("ldap://topbdii.grif.fr:2170")
//  val wms = bdii.queryWMSURIs("biomed", 120).head
//   
//  val wmsServerUrl = "https://" + wms.getHost + ":" + wms.getPort + wms.getPath
//  
//  val jobDesc = new WMSJobDescription {
//    def executable = "/bin/cat"
//    def arguments = "testis"
//    override def stdOutput = "out.txt"
//    override def stdError = "error.txt"
//    def inputSandbox = List("/tmp/testis")
//    def outputSandbox = List("out.txt" -> "/tmp/out.txt", "error.txt" -> "/tmp/error.txt")
//    override def fuzzy = true
//  }
//  
//  println(jobDesc.toJDL)
//
//  implicit val cred = auth.init("")
//  
//  val wmsJobService = new WMSJobService {
//    def url = wms
//  }
//  
//  wmsJobService.delegateProxy(auth.proxyFile)
//  val j = wmsJobService.submit(jobDesc)
//    
//  val s = untilFinished{Thread.sleep(5000); val s = wmsJobService.state(j); println(s); s}
//  
//  if(s == Done) wmsJobService.downloadOutputSandbox(jobDesc, j)
//  wmsJobService.purge(j)

//    
//  implicit val sshStorage = new SSHStorage with SSHUserPasswordAuthentication {
//    def host: String = "zebulon.iscpif.fr"
//    def user = "reuillon"
//    def password = ""
//  }
//  
//  sshStorage.list(".").foreach(println)
//  sshStorage.makeDir("/tmp/testdir")
//  
//  val os = sshStorage.openOutputStream("/tmp/testdir/test.txt")
//  os.write("Cool gridscale".toCharArray.map(_.toByte))
//  os.close
//  
//  val b = Array.ofDim[Byte](100)
//  val is = sshStorage.openInputStream("/tmp/testdir/test.txt")
//  is.read(b)
//  is.close
//  println(new String(b.map(_.toChar)))
//  
//  sshStorage.rmDir("/tmp/testdir")
  
//  implicit val sshJS = new SSHJobService with SSHUserPasswordAuthentication {
//    def host: String = "zebulon.iscpif.fr"
//    def user = "reuillon"
//    def password = ""
//  }
//  
//  val jobDesc = new SSHJobDescription {
//    def executable = "/bin/touch"
//    def arguments = "/tmp/test.ssh"
//    def workDirectory: String = "/tmp/"
//  }
//  
//  val j = sshJS.submit(jobDesc)
//  val s = untilFinished{Thread.sleep(5000); val s = sshJS.state(j); println(s); s}
//  sshJS.purge(j)
//  

//  val httpStorage = new HTTPStorage {
//    def url = "http://dist.eugridpma.info/distribution/igtf/current/accredited/tgz/"
//  }
//  
//  httpStorage.list("/").foreach(println)
//  copy(httpStorage.openInputStream("ca_UTN-USERFirst-Hardware-1.50.tar.gz"), new File("/tmp/test.tgz"), 64000, 120 * 1000)
  
}
