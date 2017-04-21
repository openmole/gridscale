package gridscale

import cats._
import cats.implicits._
import gridscale.http._
import gridscale.webdav.WebDAV._

package object webdav {
  def listProperties[M[_]: HTTP: Monad](server: Server, path: String) =
    for {
      content ← read[M](server, path, HTTP.propFind)
    } yield parsePropsResponse(content)

}
