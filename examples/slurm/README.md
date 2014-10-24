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

