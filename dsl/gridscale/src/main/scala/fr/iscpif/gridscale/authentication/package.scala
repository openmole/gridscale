package fr.iscpif.gridscale

package object authentication {
  case class UserPassword(user: String, password: String)
  case class PrivateKey(privateKey: java.io.File, password: String, user: String)
  case class PEMAuthentication(certificate: java.io.File, key: java.io.File, password: String)

}
