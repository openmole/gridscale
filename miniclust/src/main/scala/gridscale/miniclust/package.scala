package gridscale.miniclust

/*
 * Copyright (C) 2025 Romain Reuillon
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
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

import gridscale.*
import gridscale.authentication.*

export _root_.miniclust.message.{InputFile as MiniclustInputFile}
export _root_.miniclust.message.{OutputFile as MiniclustOutputFile}

case class MiniclustServer(url: String, authentication: UserPassword, insecure: Boolean = false)
case class MinclustJobDescription(
   command: String,
   inputFile: Seq[MiniclustInputFile] = Seq(),
   outputFile: Seq[MiniclustOutputFile] = Seq(),
   stdOut: Option[String] = None,
   stdErr: Option[String] = None,
   noise: String = "")

object Miniclust:

  case class Job(id: String, out: Option[String], err: Option[String])

  def withMiniclust[T](server: MiniclustServer)(f: Miniclust ?=> T): T =
    val miniclust = Miniclust(server)
    f(using miniclust)

  def apply(server: MiniclustServer) =
    val s =
      _root_.miniclust.message.Minio.Server(
        url = server.url,
        user = server.authentication.user,
        password = server.authentication.password,
        insecure = server.insecure
      )

    val bucket = _root_.miniclust.message.Minio.userBucket(s, server.authentication.user)
    new Miniclust(bucket)

case class Miniclust(bucket: _root_.miniclust.message.Minio.Bucket)

def submit(job: MinclustJobDescription)(using m: Miniclust) =
  val s = _root_.miniclust.message.Message.Submitted(
    account = _root_.miniclust.message.Account(m.bucket.name),
    command = job.command,
    inputFile = job.inputFile,
    outputFile = job.outputFile,
    stdOut = job.stdOut,
    stdErr = job.stdErr,
    noise = job.noise
  )

  Miniclust.Job(_root_.miniclust.submit.submit(m.bucket, s), job.stdOut, job.stdErr)

def state(job: Miniclust.Job)(using m: Miniclust): JobState =
  import _root_.miniclust.message.Message
  _root_.miniclust.submit.status(m.bucket, job.id) match
    case s: Message.Submitted => JobState.Submitted
    case s: Message.Running => JobState.Running
    case s: Message.Canceled => JobState.Failed
    case s: Message.Completed => JobState.Done
    case s: Message.Failed => JobState.Failed

def stdOut(job: Miniclust.Job)(using m: Miniclust): Option[String] =
  job.out.map: o =>
    _root_.miniclust.message.Minio.content(m.bucket,  _root_.miniclust.message.MiniClust.User.jobOutputPath(job.id, o))

def stdErr(job: Miniclust.Job)(using m: Miniclust): Option[String] =
  job.err.map: o =>
    _root_.miniclust.message.Minio.content(m.bucket,  _root_.miniclust.message.MiniClust.User.jobOutputPath(job.id, o))

def clean(job: Miniclust.Job)(using m: Miniclust) =
  _root_.miniclust.submit.clean(m.bucket, job.id)

def cancel(job: Miniclust.Job)(using m: Miniclust) =
  _root_.miniclust.submit.cancel(m.bucket, job.id)

def upload(local: java.io.File, remote: String)(using m: Miniclust) =
  _root_.miniclust.message.Minio.upload(m.bucket, local, remote)

def download(remote: String, local: java.io.File)(using m: Miniclust) =
  _root_.miniclust.message.Minio.download(m.bucket, remote, local)

def rmFile(path: String*)(using m: Miniclust) =
  _root_.miniclust.message.Minio.delete(m.bucket, path*)

def rmDir(path: String)(using m: Miniclust) =
  _root_.miniclust.message.Minio.deleteRecursive(m.bucket, path)

def exists(path: String)(using m: Miniclust) =
  _root_.miniclust.message.Minio.exists(m.bucket, path)