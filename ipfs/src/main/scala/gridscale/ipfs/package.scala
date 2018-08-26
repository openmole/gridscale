package gridscale

import io.ipfs.api._
import io.ipfs.multihash._
import effectaside._
import java.io._
import java.nio.ByteBuffer
import java.util.Base64

import squants._
import time.TimeConversions._
import information.InformationConversions._

package object ipfs {

  case class SubMessage(data: String, seqno: Long, from: Array[Byte])

  object IPFS {
    def apply(address: String, timeout: Time = 5 minutes) = Effect(new IPFS(address, timeout))
  }

  // FIXME IPFS lib has not timeout managment... reimplement it from apache http
  class IPFS(val address: String, timeout: Time) {
    val bufferSize = 1 megabytes
    private val ipfs = new io.ipfs.api.IPFS(address)

    def add(file: File) = {
      val content = new NamedStreamable.FileWrapper(file)
      val addResult = gridscale.tools.timeout(ipfs.add(content).get(0))(timeout)
      addResult.hash.toString
    }

    def get(address: String, file: File) = {
      val filePointer = Multihash.fromBase58(address)
      val fileContents = ipfs.catStream(filePointer)
      try gridscale.tools.copy(fileContents, file, bufferSize.toBytes.toInt, timeout)
      finally fileContents.close()
    }

    def pin(address: String): Unit = {
      ipfs.pin.add(Multihash.fromBase58(address))
    }

    def unpin(address: String, recursive: Boolean = false) = {
      ipfs.pin.rm(Multihash.fromBase58(address), recursive)
    }

    def sub(topic: String) = {
      import collection.JavaConverters._

      val received = gridscale.tools.timeout {
        val testsub = ipfs.pubsub.sub(topic)
        val received = testsub.get().asInstanceOf[java.util.HashMap[String, String]].asScala
        received
      }(timeout)

      val data = new String(Base64.getDecoder.decode(received("data")))
      val seqno = ByteBuffer.wrap(Base64.getDecoder.decode(received("seqno"))).asLongBuffer().get()
      val from = Base64.getDecoder.decode(received("from"))
      SubMessage(data = data, seqno = seqno, from = from)
    }

    def pub(topic: String, message: String) =
      gridscale.tools.timeout { ipfs.pubsub.pub(topic, message) }(timeout)

  }

  def add(file: File)(implicit ipfs: Effect[IPFS]) = ipfs().add(file)
  def get(address: String, file: File)(implicit ipfs: Effect[IPFS]) = ipfs().get(address, file)
  def pin(address: String)(implicit ipfs: Effect[IPFS]) = ipfs().pin(address)
  def unpin(address: String, recursive: Boolean)(implicit ipfs: Effect[IPFS]) = ipfs().unpin(address, recursive)
  def sub(topic: String)(implicit ipfs: Effect[IPFS]) = ipfs().sub(topic)
  def pub(topic: String, message: String)(implicit ipfs: Effect[IPFS]) = ipfs().pub(topic, message)

}
