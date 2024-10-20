package gridscale.local


object LocalExecExample extends App {

  val jobDescription = "cd /tmp && cat /etc/passwd"

  def res =
    Local.execute(jobDescription)

  println(res)


}
