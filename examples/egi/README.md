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

