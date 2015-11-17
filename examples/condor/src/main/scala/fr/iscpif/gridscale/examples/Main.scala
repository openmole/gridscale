/*
 * Copyright (C) 2012 Romain Reuillon
 * Copyright (C) 2014 Jonathan Passerat-Palmbach
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

import fr.iscpif.gridscale._
import java.io.File
import fr.iscpif.gridscale.ssh._
import fr.iscpif.gridscale.condor._
import authentication._

object Main {

  def submitEchoAndDone(condorService: CondorJobService) = {

    println("a job is successfully submitted, runs then its results are retrieved normally")

    println("a simple job")
    val description = new CondorJobDescription {
      def executable = "/bin/echo"
      def arguments = "success > test_success.txt"
      def workDirectory = "/homes/jpassera/toto"
    }

    println("job to submit:")
    println(description.toCondor)

    val j = condorService.submit(description)
    println("the job has been submitted")

    val s1 = condorService.state(j)
    println("it can be monitored")
    println("Job is " + s1)

    println("it should complete one day or another")
    val s2 = condorService.untilFinished(j) { s ⇒ println(s); Thread.sleep(5000) }

    condorService.purge(j)
  }

  def submitAndCancel(condorService: CondorJobService) = {

    println("a job is successfully submitted, then cancelled")

    println("an infinite job")
    val description = new CondorJobDescription {
      def executable = "/usr/bin/find"
      def arguments = "/"
      def workDirectory = "/homes/jpassera/toto"
    }

    println("then the job has been submitted")
    val j = condorService.submit(description)
    println(description.toCondor)

    println("it should be running")
    println(condorService.state(j))

    println("it can be cancelled")
    val s1 = condorService.cancel(j)

    println("it should appear as done")
    val s2 = condorService.untilFinished(j) { s ⇒ println(s); Thread.sleep(5000) }

    condorService.purge(j)
  }

  def submitWithRequirements(condorService: CondorJobService) = {

    println("a job is successfully submitted, requesting a tesla&fermi node")

    println("a Java job")
    val description = new CondorJobDescription {
      def executable = "/usr/bin/java"
      def arguments = "-version"
      def workDirectory = "/homes/jpassera/toto"
      override def requirements = """JavaVersion == "1.7.0_03"""" &&
        ("""OpSysShortName == "Ubuntu"""" &&
          ("OpSysMajorVer == 14" || "OpSysMajorVer == 12" || "OpSysMajorVer == 13")
        )
    }

    println("then the job has been submitted")
    val j = condorService.submit(description)
    println(description.toCondor)

    println("it should be running on a node with the particular requirements")
    println(condorService.state(j))

    println("it should appear as done")
    val s2 = condorService.untilFinished(j) { println }

    condorService.purge(j)
  }

  def main(argv: Array[String]): Unit = {

    val (host, username, password, privateKeyPath) = argv match {
      case Array(h, u, p, pKP)        ⇒ (h, u, p, pKP)
      case Array(h, u, p, null)       ⇒ (h, u, p, null)
      case Array(h, u, null, null)    ⇒ (h, u, null, null)
      case Array(h, null, null, null) ⇒ (h, null, null, null)
      case _                          ⇒ throw new RuntimeException("Bad arguments")
    }

    def credential = PrivateKey(username, new File(privateKeyPath), password)

    println("Condor example with:\n" +
      "host = " + host + "\n" +
      "username = " + username + "\n" +
      "password = " + password + "\n" +
      "privateKeyPath = " + privateKeyPath + "\n")

    val condorService = CondorJobService(host)(credential)

    submitEchoAndDone(condorService)
    submitAndCancel(condorService)
    submitWithRequirements(condorService)
  }

}