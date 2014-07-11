GridScale
=========

GridScale is a scala library for accessing various file and batch system. For the time being it supports:
* Glite / EMI, the European grid middleware,
* Remote SSH server,
* PBS clusters,
* SLURM clusters,
* SGE clusters,
* OAR clusters,
* Condor flocks
* HTTP file lists,
* DIRAC job pilot system.

Licence
-------

GridScale is licenced under the GNU Affero GPLv3 software licence. 


Imports
-------
In order to use gridscale you should import the folowing namespaces:

    import fr.iscpif.gridscale._


Examples
--------

Up to date examples are avialable in the example directory.

### SSH server
To access a storage through **SSH**:

    val sshStorage = new SSHStorage with SSHUserPasswordAuthentication {
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

To run a process on a remote server through **SSH**:

    val sshJS = new SSHJobService with SSHUserPasswordAuthentication {
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

### PBS
To submit a job on a **PBS** cluster:

    val pbsService = new PBSJobService with SSHPrivateKeyAuthentication {
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


### SLURM
To submit a job on a **SLURM** cluster:

    val slurmService = new SLURMJobService with SSHPrivateKeyAuthentication {
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


### Condor
To submit a job on a **Condor** flock:

    val condorService = new CondorJobService with SSHPrivateKeyAuthentication {
      def host = inHost
      def user = inUsername
      def password = inPassword
      def privateKey = new File(inPrivateKeyPath)
    }

    val description = new CondorJobDescription {
      def executable = "/bin/echo"
      def arguments = "success > test_success.txt"
      def workDirectory = "/homes/user"
    }

    val j = condorService.submit(description)
    val s = untilFinished { Thread.sleep(5000); val s = slurmService.state(j); println(s); s }

    condorService.purge(j)


### GLite / EMI
To **submit a job** on the biomed VO of the EGI grid:


    implicit val auth = new P12VOMSAuthentication {
      def serverURL = "voms://cclcgvomsli01.in2p3.fr:15000/O=GRID-FR/C=FR/O=CNRS/OU=CC-IN2P3/CN=cclcgvomsli01.in2p3.fr"
      def voName = "biomed"
      def fquan = None
      def lifeTime = 24 * 3600
      def certificate = new File("/path/to/your/certificate.p12")
      def password = "password"
    }.cache(1 -> HOURS)

    val bdii = new BDII("ldap://topbdii.grif.fr:2170")
    val wms = bdii.queryWMS("biomed", 120).head

    val jobDesc = new WMSJobDescription {
      def executable = "/bin/cat"
      def arguments = "testis"
      override def stdOutput = "out.txt"
      override def stdError = "error.txt"
      def inputSandbox = List(new File("/tmp/testis"))
      def outputSandbox = List("out.txt" -> new File("/tmp/out.txt"), "error.txt" -> new File("/tmp/error.txt"))
      override def fuzzy = true
    }

    val j = wms.submit(jobDesc)
      
    val s = untilFinished{Thread.sleep(5000); val s = wms.state(j); println(s); s}
    
    if(s == Done) wms.downloadOutputSandbox(jobDesc, j)
    wms.purge(j)

To **acces a storage** of the biomed VO of the EGI grid:

    implicit val auth = new P12VOMSAuthentication {
      def serverURL = "voms://cclcgvomsli01.in2p3.fr:15000/O=GRID-FR/C=FR/O=CNRS/OU=CC-IN2P3/CN=cclcgvomsli01.in2p3.fr"
      def voName = "biomed"
      def fquan = None
      def lifeTime = 24 * 3600
      def certificate = new File("/path/to/your/certificate.p12")
      def password = "password"
    }.cache(1 -> HOURS)

    val bdii = new BDII("ldap://topbdii.grif.fr:2170")
    val srm = bdii.querySRM("biomed", 120).head
    
    srm.listNames("/").foreach(println)

Submit a long running job with **MyProxy**:

    implicit val auth = new P12VOMSAuthentication {
      def serverURL = "voms://cclcgvomsli01.in2p3.fr:15000/O=GRID-FR/C=FR/O=CNRS/OU=CC-IN2P3/CN=cclcgvomsli01.in2p3.fr"
      def voName = "biomed"
      def proxyFile = new File("/tmp/proxy.x509")
      def fquan = None
      def lifeTime = 24 * 3600
      def certificate = new File("/home/reuillon/.globus/certificate.p12")
    }.cache(1 -> HOURS)
  
    val myProxy = new MyProxy {
      def host = "myproxy.cern.ch"
    }
    
    myProxy.delegate(auth(), 6000)
     
    val jobDesc = new WMSJobDescription {
      ...
      def myProxyServer = Some("myproxy.cern.ch")
    }

To submit a job to **DIRAC**:

    val dirac = new DIRACJobService with P12HTTPSAuthentication {
      def group = "biomed_user"
      def service = "https://ccdirac06.in2p3.fr:9178"
      def certificate = new File("/path/to/your/certificate.p12")
      def password = "youpassword"
    }
  
    val job = new DIRACJobDescription {
      def executable = "/bin/cat"
      def arguments = "test"
      def inputsandbox = Seq(new File("/tmp/test"))
      override def cpuTime = Some(3600)
      override def platforms = Seq(DIRACJobDescription.linux_x86_64_glibc_2_5)
    }
  
    val id = dirac.submit(job)


  SBT 
-------------

GridScale is cross compiled to serveral versions of scala. To use on of its modules add a dependency like:

    libraryDependencies += "fr.iscpif.gridscale" %% "gridscalepbs" % version
