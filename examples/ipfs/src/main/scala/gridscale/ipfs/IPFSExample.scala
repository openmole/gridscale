package gridscale.ipfs

import better.files._
import gridscale.http
object IPFSExample extends App {

  val api = IPFSAPI(s"http://localhost:5001")

  http.HTTP.withHTTP:
    val testFile = File.newTemporaryFile()
    testFile write "Life is great!"
  
    val hash = add(api, testFile.toJava)
    println(s"Hash is $hash")
  
    val testGet = File.newTemporaryFile()
    println(cat(api, hash))

}
