
package fr.iscpif.gridscale

package object storage {
  
  sealed trait FileType
  case object DirectoryType extends FileType
  case object FileType extends FileType
  case object LinkType extends FileType
  
  implicit val unitImplicit: Unit = Unit
  
}