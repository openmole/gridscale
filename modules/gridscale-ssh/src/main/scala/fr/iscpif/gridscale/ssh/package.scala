/*
 * Copyright (C) 2015 Romain Reuillon
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package fr.iscpif.gridscale

import fr.iscpif.gridscale.authentication._
import net.schmizz.sshj.SSHClient

package object ssh {

  implicit def sshUserPassword(userPassword: UserPassword) = new SSHAuthentication {
    override def authenticate(c: SSHClient): Unit =
      try c.authPassword(userPassword.user, userPassword.password)
      catch {
        case e: Throwable ⇒ throw AuthenticationException("Error during ssh login/password authentication", e)
      }
  }

  implicit def sshPrivateKey(privateKey: PrivateKey) = new SSHAuthentication {
    override def authenticate(c: SSHClient) =
      try {
        val kp = c.loadKeys(privateKey.privateKey.getAbsolutePath, privateKey.password)
        c.authPublickey(privateKey.user, kp)
      } catch {
        case e: Throwable ⇒ throw AuthenticationException("Error during ssh key authentication", e)
      }
  }

}
