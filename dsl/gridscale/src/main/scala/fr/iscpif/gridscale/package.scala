package fr.iscpif

package object gridscale {

  sealed trait FileType

  object FileType {
    case object Directory extends FileType
    case object File extends FileType
    case object Link extends FileType
    case object Unknown extends FileType
  }

  case class ListEntry(name: String, `type`: FileType, modificationTime: Option[Long] = None)

  sealed trait JobState

  object JobState {
    case object Submitted extends JobState
    case object Running extends JobState
    case object Done extends JobState
    case object Failed extends JobState
  }

}
