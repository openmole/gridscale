/*
 * Copyright (C) 2014 Romain Reuillon
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

package fr.iscpif.gridscale.tools.shell

trait BashShell <: Shell {

  // bash -ci will fake an interactive shell (-i) in order to load the config files
  // as an interactive ssh shell would (~/.bashrc, /etc/bashrc)
  // and run the sequence of command without interaction (-c)
  override def command(cmd: String) = new Command {
    // ugly attempt to "generify" user shell
    // assumption 1: Bash is installed on the target host
    // assumption 2: the env variables required for a successful execution are defined in .bashrc
    // => bash -ci ensures that .bashrc is loaded
    // => bash << EOF prevents the defaut shell (which might not be bash) to process the command
    // resulting in is this combination...
    override def toString = "bash -ci bash << EOF \n" +
      cmd + "\n" +
      "EOF"
  }
}
