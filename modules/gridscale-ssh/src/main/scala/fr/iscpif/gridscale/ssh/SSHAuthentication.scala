/*
 * Copyright (C) 2012 Romain Reuillon
 * Copyright (C) 2015 Jonathan Passerat-Palmbach
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

package fr.iscpif.gridscale.ssh

import fr.iscpif.gridscale.authentication.Credential
import net.schmizz.sshj._
import net.schmizz.sshj.transport.verification.PromiscuousVerifier

trait SSHAuthentication <: Credential {
  type A >: SSHAuthentication
  def credential = this
  // instantiated only once and not for each sshj SSHClient
  // see https://groups.google.com/d/msg/sshj-users/p-cjao1MiHg/nFZ99-WEf6IJ
  lazy val sshDefaultConfig = new DefaultConfig()

  def connect(host: String, port: Int) = {
    val ssh = new SSHClient(sshDefaultConfig)
    // disable strict host key checking
    ssh.getTransport.addHostKeyVerifier(new PromiscuousVerifier)
    ssh.connect(host, port)

    try authenticate(ssh)
    catch {
      case t: Throwable ⇒
        ssh.disconnect
        throw t
    }
    ssh
  }

  def authenticate(c: SSHClient)
}
