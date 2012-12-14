package fr.iscpif.gridscale.examples

import fr.iscpif.gridscale.jobservice._
import fr.iscpif.gridscale.authentication.SSHPrivateKeyAuthentication
import java.io.File

object Main {

  def submitEchoAndDone ( inHost: String, inUsername: String, inPassword: String, inPrivateKeyPath: String ) = {
    
    println("a job is successfully submitted, runs then its results are retrived normally")

    println("a slurm environment using an SSH privatekey authentication")
    implicit val slurmService = new SLURMJobService with SSHPrivateKeyAuthentication {
      def host = inHost
      def user = inUsername
      def password = inPassword
      def privateKey = new File(inPrivateKeyPath)
    }

    println("a simple job")
    val description = new SLURMJobDescription {
      def executable = "/bin/echo"
      def arguments = "success > test_success.txt"
      def workDirectory = "/home/jopasserat/toto"
    }
    
    println("job to submit:")
    println(description.toSLURM)
       
    val j = slurmService.submit(description)
   println("the job has been submitted") 

    val s1 = slurmService.state(j)
    println("it can be monitored")
    println("Job is " + s1)

    println("it should complete one day or another")
    val s2 = untilFinished { Thread.sleep(5000); val s = slurmService.state(j); println(s); s }
    
    slurmService.purge(j)
  }

  
  def submitAndCancel ( inHost: String , inUsername: String, inPassword: String, inPrivateKeyPath: String ) = {

    println("a job is successfully submitted, then cancelled")

    println("a slurm environment using an SSH privatekey authentication")
    implicit val slurmService = new SLURMJobService with SSHPrivateKeyAuthentication {
      def host = inHost
      def user = inUsername
      def password = inPassword
      def privateKey = new File(inPrivateKeyPath)
    }

    println("an infinite job")
    val description = new SLURMJobDescription {
      def executable = "/usr/bin/find"
      def arguments = "/"
      def workDirectory = "/home/jopasserat/toto"
    }

    println("then the job has been submitted")
    val j = slurmService.submit(description)
    println(description.toSLURM)

    println("it should be running")
    println(slurmService.state(j))
    
    println("it can be cancelled")
    val s1 = slurmService.cancel(j)

    println("it should appear as done")
    val s2 = untilFinished { Thread.sleep(5000); val s = slurmService.state(j); println(s); s }
    
    slurmService.purge(j)
  }

  
  def main(argv: Array[String]): Unit = {
    
    val (host, username, password, privateKeyPath) = argv match {
      case Array(h, u, p, pKP) => (h, u, p, pKP)
      case Array(h, u, p, null) => (h, u, p, null)
      case Array(h, u, null, null) => (h, u, null, null)
      case Array(h, null, null, null) => (h, null, null, null)
      case _ => throw new RuntimeException ("Bad arguments")
    }
    
    println ("SLURM example with:\n" +
    		"host = " + host + "\n" +
    		"username = " + username + "\n" +
    		"password = " + password + "\n" +
    		"privateKeyPath = " + privateKeyPath + "\n"
    )
    
    submitEchoAndDone (host, username, password, privateKeyPath)
    submitAndCancel   (host, username, password, privateKeyPath)
  }

}