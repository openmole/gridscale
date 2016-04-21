/*
 * Copyright (C) 2016 Adrian Draghici
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

package fr.iscpif.gridscale.examples

import fr.iscpif.gridscale.aws.AWSJobService
import resource.managed

object Main extends App {

  val awsService = AWSJobService("us-east-1", "adrian", "gridscale", "/Users/adrian/.aws/credentials.csv", "/Users/adrian/.ssh/id_rsa")

  managed(awsService) acquireAndGet {
    aws ⇒
      {
        println("starting stuff")
        aws.start()
        println(aws.host)
        println("running test script")
        aws.testScript()
      }
  }
}
