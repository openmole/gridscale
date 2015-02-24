### OAR
To submit a job to **OAR**:

    val service = new OARJobService with SSHUserPasswordAuthentication {
      def host = "172.17.0.4"
        def user = "docker"
        def password = "docker"
    }

    val description = new OARJobDescription {
      def executable = "/bin/echo"
        def arguments = "hello wold"
        def workDirectory = "/data/"
        override def core = Some(1)
        override def cpu = Some(1)

        override def wallTime = Some(1 hour)
    }

    val j = service.submit(description)

