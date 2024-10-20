package gridscale

import java.io.*
import java.net.URLEncoder
import java.util.zip.GZIPInputStream
import gridscale.http.*
import io.circe.generic.auto.*
import io.circe.parser.*
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.utils.IOUtils
import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.entity.mime.content.FileBody
import squants.*
import squants.time.TimeConversions.*

package object ipfs {

  case class SubMessage(data: String, seqno: Long, from: Array[Byte])

  object IPFSAPI {
    def toHTTPServer(ipfsAPI: IPFSAPI) = http.Server(ipfsAPI.url, ipfsAPI.timeout)
    def toPath(ipfsAPI: IPFSAPI, function: String) = s"${ipfsAPI.version}/$function"
  }

  case class IPFSAPI(url: String, version: String = "/api/v0", timeout: Time = 1 minutes)

  object LS {
    case class Link(Name: String, Hash: String, Size: Int, Type: Int)
    case class Entries(Hash: String, Links: Vector[Link])
    case class Results(Objects: Vector[Entries]) //objs: Vector[Obj])
  }

  object Add {
    case class Result(Name: String, Hash: String, Size: Int)
  }

  def get(ipfsAPI: IPFSAPI, hash: String, f: File)(using HTTP) =
    def read(is: InputStream) = tools.FileSystem.writeStream(f) { os ⇒ tools.copyStream(new GZIPInputStream(is), os) }
    getStream(ipfsAPI, hash, read)

  def getStream[T](ipfsAPI: IPFSAPI, hash: String, read: InputStream ⇒ T, archive: Boolean = false)(using HTTP) =
    http.readStream(IPFSAPI.toHTTPServer(ipfsAPI), s"${IPFSAPI.toPath(ipfsAPI, "get")}?arg=$hash&archive=$archive&compress=true", read)

  def cat(ipfsAPI: IPFSAPI, hash: String)(using HTTP) =
    http.read(IPFSAPI.toHTTPServer(ipfsAPI), s"${IPFSAPI.toPath(ipfsAPI, "cat")}?arg=$hash")

  def catStream[T](ipfsAPI: IPFSAPI, hash: String, f: InputStream ⇒ T)(using HTTP) =
    http.readStream(IPFSAPI.toHTTPServer(ipfsAPI), s"${IPFSAPI.toPath(ipfsAPI, "cat")}?arg=$hash", f)

  def ls(ipfsAPI: IPFSAPI, hash: String)(using HTTP) =
    def body = http.read(IPFSAPI.toHTTPServer(ipfsAPI), s"${IPFSAPI.toPath(ipfsAPI, "ls")}?arg=$hash")
    val entries = decode[LS.Results](body).right.get

    def linkToEntry(l: LS.Link) =
      l.Type match
        case 2 ⇒ ListEntry(l.Name, FileType.File)
        case 1 ⇒ ListEntry(l.Name, FileType.Directory)
        case _ ⇒ ListEntry(l.Name, FileType.Unknown)

    entries.Objects.flatMap { _.Links.map { linkToEntry } }

  def add(ipfsAPI: IPFSAPI, f: File)(using HTTP) =
    import java.nio.file._

    def entity() =
      val entity = MultipartEntityBuilder.create()
      def encode(e: String) = URLEncoder.encode(e, "UTF-8")

      val rootPath = f.toPath
      val allFiles = Files.walk(rootPath)
      try allFiles.forEach { f ⇒
        val name = rootPath.getParent.relativize(f)
        if (!f.toFile.isDirectory) {
          val b = new FileBody(f.toFile, ContentType.APPLICATION_OCTET_STREAM, encode(name.toString))
          entity.addPart(name.toString, b)
        } else entity.addBinaryBody(name.toString, Array.emptyByteArray, ContentType.create("application/x-directory"), f.getFileName.toString)
      } finally allFiles.close()
      entity.build()

    val r = gridscale.http.read(IPFSAPI.toHTTPServer(ipfsAPI), IPFSAPI.toPath(ipfsAPI, "add") + "?r=true", Post(entity))
    decode[Add.Result](r.split("\n").last).right.get.Hash

  def add(ipfsAPI: IPFSAPI, is: InputStream)(using HTTP) =
    try
      def entity() =
        val entity = MultipartEntityBuilder.create()
        entity.addBinaryBody("file", is)
        entity.build()

      val r = gridscale.http.read(IPFSAPI.toHTTPServer(ipfsAPI), IPFSAPI.toPath(ipfsAPI, "add"), Post(entity))

      decode[Add.Result](r).right.get.Hash
    finally is.close()

  def pin(ipfsAPI: IPFSAPI, hash: String)(using HTTP) =
    gridscale.http.read(IPFSAPI.toHTTPServer(ipfsAPI), IPFSAPI.toPath(ipfsAPI, "pin/add") + s"?arg=$hash")

  def unpin(ipfsAPI: IPFSAPI, hash: String)(using HTTP) =
    gridscale.http.read(IPFSAPI.toHTTPServer(ipfsAPI), IPFSAPI.toPath(ipfsAPI, "pin/rm") + s"?arg=$hash")

}
