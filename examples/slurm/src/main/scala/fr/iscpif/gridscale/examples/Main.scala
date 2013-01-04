/*
 * Copyright (C) 2012 Romain Reuillon
 * Copyright (C) 2012 Jonathan Passerat-Palmbach
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

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

  def submitWithGres ( inHost: String , inUsername: String, inPassword: String, inPrivateKeyPath: String ) = {

    println("a CUDA job is successfully submitted, requesting a gres")

    println("a slurm environment using an SSH privatekey authentication")
    implicit val slurmService = new SLURMJobService with SSHPrivateKeyAuthentication {
      def host = inHost
      def user = inUsername
      def password = inPassword
      def privateKey = new File(inPrivateKeyPath)
    }

    println("an CUDA job")
    val description = new SLURMJobDescription {
      def executable = "/opt/cuda/C/bin/linux/release/matrixMul"
      def arguments = ""
      def workDirectory = "/home/jopasserat/toto"
      override def gres = List(new Gres("gpu", 1))
    }

    println("then the job has been submitted")
    val j = slurmService.submit(description)
    println(description.toSLURM)

    println("it should be running on a gres")
    println(slurmService.state(j))

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
    
//    submitEchoAndDone (host, username, password, privateKeyPath)
//    submitAndCancel   (host, username, password, privateKeyPath)
    submitWithGres    (host, username, password, privateKeyPath)
  }

}