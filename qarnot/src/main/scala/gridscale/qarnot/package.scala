package gridscale.qarnot

/*
 * Copyright (C) 2023 Romain Reuillon
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import gridscale.effectaside.*
import gridscale.http
import io.circe.*
import io.circe.generic.auto.*
import io.circe.parser.*
import io.circe.syntax.*
import org.apache.commons.codec.Charsets
import org.apache.http.entity.StringEntity
import org.apache.http.entity.mime.{FormBodyPartBuilder, MultipartEntityBuilder}
import org.apache.http.entity.mime.content.StringBody

case class JobDescription(command: String)
type Token = String


def submit(job: JobDescription, token: String)(using Effect[http.HTTP]) =

  object Constant:
    given Conversion[(String, String), Constant] = (k, v) => Constant(k, v)

  case class Constant(key: String, value: String)
  case class Task(
    name: String,
    profile: String,
    instanceCount: Int,
    constants: Seq[Constant] = Seq())

  val t =
    Task(
      "test",
      "docker-batch",
      1,
      Seq(
        "DOCKER_SRV" -> "https://registry-1.docker.io",
        "DOCKER_REPO" -> "library/ubuntu",
        "DOCKER_TAG" -> "latest",
        "RESOURCES_PATH" -> "/job",
        "DOCKER_CMD"  -> "echo gridscale",
        "QARNOT_DISABLE_CPU_BOOST" -> "false"
      )
    )

  val server = http.buildServer(s"https://${Qarnot.url}")

  def entity() = new StringEntity(t.asJson.noSpaces, Charsets.UTF_8.toString)

  val headers =
    Seq(
      "Content-Type" -> "application/json",
      "Authorization" -> token
    )

  val post = http.Post(entity, headers)

  http.read(server, "/tasks", post)

//    def files() = {
//      val builder = MultipartEntityBuilder.create()
//      jobDescription.inputSandbox.foreach {
//        f ⇒ builder.addBinaryBody(f.getName, f)
//      }
//      builder.addTextBody("access_token", token.token)
//      builder.addTextBody("manifest", JobDescription.toJSON(jobDescription, jobGroup))
//      builder.build
//    }
//
//    val r = gridscale.http.read(server.server, jobsLocation, Post(files))
//    val id = (parse(r) \ "jids")(0).extract[String]
//    JobID(id, Some(jobDescription))

object Qarnot:

  lazy val url = "api.qarnot.com"

  class Interpreters:
    implicit val fileSystemInterpreter: Effect[FileSystem] = FileSystem()
    implicit val httpInterpreter: Effect[http.HTTP] = http.HTTP()

  def apply[T](f: Interpreters ⇒ T) =
    val interpreters = new Interpreters()
    f(interpreters)
