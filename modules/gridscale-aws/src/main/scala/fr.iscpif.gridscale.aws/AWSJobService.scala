/*
 * Copyright (C) 2016 Adrian Draghici
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

package fr.iscpif.gridscale.aws

import java.io.File

import fr.iscpif.gridscale.authentication.PrivateKey
import fr.iscpif.gridscale.aws.AWSJobService.AWSJob
import fr.iscpif.gridscale.jobservice._
import fr.iscpif.gridscale.ssh._
import fr.iscpif.gridscale.ssh.SSHJobService._
import fr.iscpif.gridscale.tools.shell.{ Command, BashShell }
import org.jclouds.ContextBuilder
import org.jclouds.aws.ec2.compute.AWSEC2TemplateOptions
import org.jclouds.compute.domain.{ NodeMetadata, OsFamily, Template }
import org.jclouds.compute.{ ComputeService, ComputeServiceContext }
import org.jclouds.ec2.domain.InstanceType
import org.jclouds.sshj.config.SshjSshClientModule

import scala.collection.JavaConverters._
import scala.concurrent.duration._

import scala.io.Source

object AWSJobService {

  val Provider = "aws-ec2"
  val CoordinatorImageId = "ami-b7d8cedd"
  val Group = "gridscale-aws"
  val User = "ubuntu"

  def apply(region: String, awsUser: String, awsKeypairName: String, awsCredentialsPath: String, privateKeyPath: String) = {
    val (_region, _awsKeypairName) = (region, awsKeypairName)
    val (awsKeyId, awsSecretKey) = readAWSCredentials(awsUser, awsCredentialsPath)
    new AWSJobService {
      override val region = _region
      override val awsKeypairName = _awsKeypairName
      override val client = createClient(awsKeyId, awsSecretKey)
      override val credential = sshPrivateKey(PrivateKey(User, new File(privateKeyPath), ""))
      override lazy val host = coordinator.getPublicAddresses.asScala.head
      override val port = 22
      override val timeout = 1 minute
    }
  }

  class AWSJob(val description: AWSJobDescription, val id: String)

  private def createClient(awsKeyId: String, awsSecretKey: String): ComputeService = {
    ContextBuilder.newBuilder(Provider)
      .credentials(awsKeyId, awsSecretKey)
      .modules(Set(new SshjSshClientModule).asJava)
      .buildView(classOf[ComputeServiceContext])
      .getComputeService
  }

  private def readAWSCredentials(user: String, path: String): (String, String) = {
    resource.managed(Source.fromFile(path)) acquireAndGet {
      src ⇒
        {
          val Array(_, keyId, secretKey) = src.getLines.drop(1).map(_.split(",")).filter(line ⇒ user == extractUser(line)).next
          (keyId, secretKey)
        }
    }
  }

  private def createImageId(region: String, ami: String): String = region + "/" + ami

  private def extractUser(credentialLine: Array[String]) = credentialLine(0).stripPrefix("\"").stripSuffix("\"")
}

import fr.iscpif.gridscale.aws.AWSJobService._

trait AWSJobService extends JobService with SSHHost with SSHStorage with BashShell with AutoCloseable {
  type J = AWSJob
  type D = AWSJobDescription

  def region: String
  def awsKeypairName: String
  def client: ComputeService
  var coordinator: NodeMetadata = _

  def start(): Unit = {
    coordinator = client.createNodesInGroup(Group, 1, createTemplate(region, client)).asScala.head
    // Wait for a short period to make sure that the VM is initialized and ports are opened
    println("waiting for initialization")
    MINUTES.sleep(1)
  }

  def kill(): Unit = {
    println(s"shutting down coordinator ${coordinator.getId}")
    client.destroyNode(coordinator.getId)
    println("coordinator down")
  }

  def testScript(): Unit = withConnection { implicit connection ⇒
    exec("mkdir -p testdir")
    exec("echo working > testdir/file.txt")
  }

  def close(): Unit = client.getContext.close()

  def submit(description: D): J = ???

  def cancel(job: J): Unit = ???

  def state(job: J): JobState = ???

  def purge(job: J): Unit = ???

  private def createTemplate(region: String, client: ComputeService): Template = {
    val template = client.templateBuilder()
      .hardwareId(InstanceType.T1_MICRO)
      .osFamily(OsFamily.UBUNTU)
      .imageId(createImageId(region, CoordinatorImageId))
      .build()

    val options = template.getOptions.as(classOf[AWSEC2TemplateOptions]).keyPair(awsKeypairName)
    template
  }

}
