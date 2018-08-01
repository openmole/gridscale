package gridscale.ipfs

import better.files._

object IPFSExample extends App {

  implicit val ipfs = IPFS(s"/ip4/104.45.15.171/tcp/5001")

  val testFile = File.newTemporaryFile()
  testFile write "Life is great!"

  val hash = add(testFile.toJava)
  println(s"Hash is $hash")

  val testGet = File.newTemporaryFile()
  get(hash, testGet.toJava)

  println(scala.io.Source.fromFile(testGet.toJava).mkString)
  pub("toto", "test")

}
