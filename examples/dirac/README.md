### DIRAC
To submit a job to **DIRAC**:

    val dirac = new DIRACJobService with P12HTTPSAuthentication {
      def group = "biomed_user"
      def service = "https://ccdirac06.in2p3.fr:9178"
      def certificate = new File("/path/to/your/certificate.p12")
      def password = "yourpassword"
    }
  
    val job = new DIRACJobDescription {
      def executable = "/bin/cat"
      def arguments = "test"
      def inputsandbox = Seq(new File("/tmp/test"))
      override def cpuTime = Some(3600)
      override def platforms = Seq(DIRACJobDescription.linux_x86_64_glibc_2_5)
    }
  
    val id = dirac.submit(job)

