/*
 * Copyright (C) 2012 Romain Reuillon
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

package fr.iscpif.gridscale.authentication

import java.io.File
import java.io.FileInputStream
import org.glite.voms.contact.VOMSProxyInit
import org.gridforum.jgss.ExtendedGSSManager
import org.gridforum.jgss.ExtendedGSSCredential
import org.ietf.jgss.GSSCredential

trait ProxyFileAuthentication {
  
  def proxy: File
  
  def init = {
    val proxyBytes = Array.ofDim[Byte](proxy.length.toInt)
    val in = new FileInputStream(proxy)
    try in.read(proxyBytes)
    finally in.close

    ExtendedGSSManager.getInstance.asInstanceOf[ExtendedGSSManager].createCredential(
      proxyBytes,
      ExtendedGSSCredential.IMPEXP_OPAQUE,
      GSSCredential.DEFAULT_LIFETIME,
      null, // use default mechanism: GSI
      GSSCredential.INITIATE_AND_ACCEPT)
  }

}

