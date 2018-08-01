package gridscale

import io.ipfs.api._
import io.ipfs.multihash._
import effectaside._
import java.io._
import squants._
import time.TimeConversions._
import information.InformationConversions._

package object ipfs {

  object IPFS {
    def apply(address: String, timeout: Time = 5 minutes) = new IPFS(address, timeout)
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
  }

  def add(file: File)(implicit ipfs: Effect[IPFS]) = ipfs().add(file)
  def get(address: String, file: File)(implicit ipfs: Effect[IPFS]) = ipfs().get(address, file)

}
