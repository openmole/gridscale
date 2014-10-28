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

