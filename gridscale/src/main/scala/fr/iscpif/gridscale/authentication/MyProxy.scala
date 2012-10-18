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

import org.globus.myproxy.{MyProxy => GMyProxy, InitParams, InfoParams}
import org.ietf.jgss.GSSCredential

trait MyProxy {

  def host: String
  def port: Int = GMyProxy.DEFAULT_PORT
  
  def myProxy = new GMyProxy(host, port)

  def delegate(credential: GSSCredential, time: Int) = {
    val proxyParameters = new InitParams
    proxyParameters.setLifetime(time)
    myProxy.put(credential, proxyParameters)
  }
  
  def info(credential: GSSCredential) = {
    val infoParams = new InfoParams
    myProxy.info(credential, infoParams)
  }
  
}
