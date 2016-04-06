/**
 * Copyright (C) 2016 Jonathan Passerat-Palmbach
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
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

package fr.iscpif.gridscale.ssh.impl

import java.io.InputStream

import fr.iscpif.gridscale.ssh.SessionCommand

object SSHJSession {

  import net.schmizz.sshj.connection.channel.direct.Session

  def close()(implicit sshjSession: Session) = sshjSession.close()

  def exec(command: String)(implicit sshjSession: Session): SessionCommand = {
    val sshjCommand = sshjSession.exec(command)
    new SessionCommand {
      def join() = sshjCommand.join()
      def close() = sshjCommand.close()
      def getExitStatus: Int = sshjCommand.getExitStatus
      def getInputStream: InputStream = sshjSession.getInputStream
      def getErrorStream: InputStream = sshjCommand.getErrorStream
    }
  }
}
