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

import net.schmizz.sshj._
import net.schmizz.sshj.transport.verification.PromiscuousVerifier

trait SSHAuthentication {

  def connect(host: String, port: Int) = {
    val ssh = new SSHClient

    // disable strict host key checking
    ssh.disableHostChecking()
    ssh.useCompression()
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
