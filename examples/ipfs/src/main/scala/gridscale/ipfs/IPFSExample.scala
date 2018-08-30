package gridscale.ipfs

import better.files._

object IPFSExample extends App {

  implicit val ipfs = IPFS()
  val api = IPFSAPI(s"http://localhost:5001")

  val testFile = File.newTemporaryFile()
  testFile write "Life is great!"

  val hash = add(api, testFile.toJava)
  println(s"Hash is $hash")

  val testGet = File.newTemporaryFile()
  println(cat(api, hash))

}
