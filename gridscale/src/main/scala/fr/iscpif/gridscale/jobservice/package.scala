

package fr.iscpif.gridscale

package object jobservice {
  
  sealed trait JobState
  case object Submitted extends JobState
  case object Running extends JobState
  case object Done extends JobState
  case object Failed extends JobState
  
  def untilFinished(f: => JobState): JobState = {
    val s = f
    if(s == Done || s == Failed) s
    else untilFinished(f)
  }
  
}