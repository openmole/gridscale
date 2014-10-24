### SGE
To submit a job to **SGE**:

    val service = new SGEJobService with SSHUserPasswordAuthentication {
      def host = "master.domain"
      def user = "login"
      def password = "password"
    }

    val description = new SGEJobDescription {
      def executable = "/bin/echo"
      def arguments = "hello wold"
      def workDirectory = service.home + "/testjob/"
    }

    val j = service.submit(description)

