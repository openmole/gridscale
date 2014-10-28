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

