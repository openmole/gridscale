package fr.iscpif.gridscale.ssh

import fr.iscpif.gridscale.authentication._

import scala.io.Source
object TestSSH extends App {

  val auth = UserPassword("reuillon", Source.fromFile("/home/reuillon/.ssh/password").getLines().next().trim)
  val dir = "/home/reuillon/.openmole/.tmp/ssh/openmole-7e3dc791-359e-4329-add4-a1f1ded72491/tmp/1493297397747/903b32f2-0d66-4eb0-bd8c-ff8489bbd5e2"

  val storage = SSHStorage("zebulon.iscpif.fr")(auth)

  storage.rmDir(dir)
}