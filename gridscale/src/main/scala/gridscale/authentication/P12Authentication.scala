/*
 * Copyright (C) 04/06/13 Romain Reuillon
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package gridscale.authentication

import java.io.{ File, FileInputStream, IOException }
import java.security.cert.X509Certificate
import scala.util.Try

object P12Authentication {
  def getAlias(ks: java.security.KeyStore) =
    val aliases = ks.aliases

    import java.security.cert._
    import collection.JavaConverters._

    aliases.asScala.find(e ⇒ ks.isKeyEntry(e)).getOrElse(throw new RuntimeException("No private key found in the P12 file"))


  def loadPKCS12Credentials(a: P12Authentication): Loaded = 
    val ks = java.security.KeyStore.getInstance("pkcs12")
    ks.load(new java.io.FileInputStream(a.certificate), a.password.toCharArray)
    val alias = getAlias(ks)
    val userCert = ks.getCertificate(alias).asInstanceOf[X509Certificate]
    val userKey = ks.getKey(alias, a.password.toCharArray).asInstanceOf[java.security.PrivateKey]
    val userChain = Array[X509Certificate](userCert)
    Loaded(userCert, userKey, userChain)

  case class Loaded(certificate: X509Certificate, key: java.security.PrivateKey, chain: Array[X509Certificate])

  def testPassword(p12Authentication: P12Authentication): Try[Boolean] =
    util.Try(loadPKCS12Credentials(p12Authentication)).map(_ ⇒ true)

}

case class P12Authentication(certificate: File, password: String)
