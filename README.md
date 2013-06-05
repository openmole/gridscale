GridScale
=========

GridScale is a scala library for accessing various file and batch system. For the time being it supports:
* Glite / EMI, the European grid middleware,
* Remote SSH server,
* PBS clusters,
* SLURM clusters,
* HTTP file lists.

Support is planned for SGE and DIRAC job pilot system.

Documentation
-------------

The scaladoc of GridScale will be available here: [scaladoc](http://romainreuillon.github.com/gridscale/scaladoc).

Licence
-------

GridScale is licenced under the GNU Affero GPLv3 software licence. 


Compiling
---------

Clone, init/update submodules, mvn install.

Imports
-------
In order to use gridscale you should import the folowing namespaces:

    import fr.iscpif.gridscale
    
    import authentication._
    import information._
    import storage._
    import jobservice._
    import tools._


Examples
--------

To access a storage through ssh:

    implicit val sshStorage = new SSHStorage with SSHUserPasswordAuthentication {
      def host: String = "server.domain"
      def user = "username"
      def password = "password"
    }
    
    sshStorage.list(".").foreach(println)
    sshStorage.makeDir("/tmp/testdir")
    
    val os = sshStorage.openOutputStream("/tmp/testdir/test.txt")
    try os.write("Cool gridscale".toCharArray.map(_.toByte))
    finally os.close
    
    val b = Array.ofDim[Byte](100)
    val is = sshStorage.openInputStream("/tmp/testdir/test.txt")
    try is.read(b)
    finally is.close
    
    println(new String(b.map(_.toChar)))
    
    sshStorage.rmDir("/tmp/testdir")

To run a process on a remote server through ssh:

    implicit val sshJS = new SSHJobService with SSHUserPasswordAuthentication {
      def host: String = "server.domain"
      def user = "user"
      def password = "password"
    }
    
    val jobDesc = new SSHJobDescription {
      def executable = "/bin/touch"
      def arguments = "/tmp/test.ssh"
      def workDirectory: String = "/tmp/"
    }
    
    val j = sshJS.submit(jobDesc)
    val s = untilFinished{Thread.sleep(5000); val s = sshJS.state(j); println(s); s}
    sshJS.purge(j)

To submit a job on a PBS cluster:

    implicit val pbsService = new PBSJobService with SSHPrivateKeyAuthentication {
      def host: String = "server.domain"
      def user = "user"
      def password = "password"
      def privateKey = new File("/path/to/key/file")
    }
    
    val description = new PBSJobDescription {
      def executable = "/bin/echo"
      def arguments = "success >test_success.txt"
      def workDirectory = "/home/user"
    }
    
    val j = pbsService.submit(description)
    val s = untilFinished{Thread.sleep(5000); val s = pbsService.state(j); println(s); s}
    pbsService.purge(j)

To submit a job on a SLURM cluster:

    implicit val slurmService = new SLURMJobService with SSHPrivateKeyAuthentication {
      def host: String = "server.domain"
      def user = "user"
      def password = "password"
      def privateKey = new File("/path/to/key/file")
    }

    val description = new SLURMJobDescription {
      def executable = "/bin/echo"
      def arguments = "success > test_success.txt"
      def workDirectory = "/home/user"
    }

    val j = slurmService.submit(description)
    val s = untilFinished { Thread.sleep(5000); val s = slurmService.state(j); println(s); s }

    slurmService.purge(j)

To submit a job on the biomed VO of the EGI grid:

    val bdii = new BDII("ldap://topbdii.grif.fr:2170")
    val wms = bdii.queryWMS("biomed", 120).head

    VOMSAuthentication.setCARepository(new File("/dir/to/you/authority/certificates/dir"))
    
    implicit val auth = new P12VOMSAuthentication {
      def serverURL = "voms://cclcgvomsli01.in2p3.fr:15000/O=GRID-FR/C=FR/O=CNRS/OU=CC-IN2P3/CN=cclcgvomsli01.in2p3.fr"
      def voName = "biomed"
      def proxyFile = new File("/tmp/proxy.x509")
      def fquan = None
      def lifeTime = 3600
      def certificate = new File("/path/to/your/certificate.p12")
    }.cache(3600).bind(wms.delegate)
    
    implicit val cred = auth.init("pasword")
     
    val jobDesc = new WMSJobDescription {
      def executable = "/bin/cat"
      def arguments = "testis"
      override def stdOutput = "out.txt"
      override def stdError = "error.txt"
      def inputSandbox = List("/tmp/testis")
      def outputSandbox = List("out.txt" -> "/tmp/out.txt", "error.txt" -> "/tmp/error.txt")
      override def fuzzy = true
    }

    val j = wms.submit(jobDesc)
      
    val s = untilFinished{Thread.sleep(5000); val s = wms.state(j); println(s); s}
    
    if(s == Done) wms.downloadOutputSandbox(jobDesc, j)
    wms.purge(j)

To acces a storage of the biomed VO of the EGI grid:

    val bdii = new BDII("ldap://topbdii.grif.fr:2170")
    val srm = bdii.querySRM("biomed", 120).head
    
    VOMSAuthentication.setCARepository(new File( "/path/to/authority/certificates/directory"))
    
    val auth = new P12VOMSAuthentication {
      def serverURL = "voms://cclcgvomsli01.in2p3.fr:15000/O=GRID-FR/C=FR/O=CNRS/OU=CC-IN2P3/CN=cclcgvomsli01.in2p3.fr"
      def voName = "biomed"
      def proxyFile = new File("/tmp/proxy.x509")
      def fquan = None
      def lifeTime = 3600
      def certificate = new File("/path/to/your/certificate.p12")
    }
  
    implicit val proxy = auth.init("password")
    
    srm.listNames("/").foreach(println)

Submit a long running job with my proxy:

    VOMSAuthentication.setCARepository(new File( "/home/reuillon/.openmole/CACertificates"))
    
    val auth = new P12VOMSAuthentication {
      def serverURL = "voms://cclcgvomsli01.in2p3.fr:15000/O=GRID-FR/C=FR/O=CNRS/OU=CC-IN2P3/CN=cclcgvomsli01.in2p3.fr"
      def voName = "biomed"
      def proxyFile = new File("/tmp/proxy.x509")
      def fquan = None
      def lifeTime = 3600
      def certificate = new File("/home/reuillon/.globus/certificate.p12")
    }
  
    implicit val proxy = auth.init("password")
    
    val myProxy = new MyProxy {
      def host = "myproxy.cern.ch"
    }
    
    myProxy.delegate(proxy, 6000)
     
    val jobDesc = new WMSJobDescription {
      ...
      def myProxyServer = Some("myproxy.cern.ch")
    }

  Maven
-------------

GridScale depend on the 2.10 version of scala. We intend to switch to sbt and support multi-version at some point in the future.

    <repository>
      <id>iscpif</id>
      <name>iscpif Repo</name>
      <url>http://maven.iscpif.fr/public/</url>
    </repository>

    <artifactId>parent</artifactId>
    <groupId>fr.iscpif.gridscale</groupId>
    <version>1.40</version>
