import cats.data.Kleisli

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

    def isFinal(s: JobState) = s == Done || s == Failed
  }

  import cats._
  import cats.implicits._
  import freedsl.system._
  import freedsl.tool._
  import squants._
  import squants.time.TimeConversions._

  def waitUntilEnded[M[_]: Monad](f: M[JobState], wait: Time = 10 seconds)(implicit system: System[M]) = {
    def pull = for {
      s ← f
      end = JobState.isFinal(s)
      _ ← if (!end) system.sleep(wait) else ().pure[M]
    } yield (end, s)

    pull.until(Kleisli { v: (Boolean, JobState) ⇒ v._1.pure[M] }).map(_._2)
  }

  case class ExecutionResult(returnCode: Int, stdOut: String, stdErr: String)

  object ExecutionResult {
    def error(command: String, executionResult: ExecutionResult) = {
      import executionResult._
      s"Unexpected return code $returnCode, when running $command (stdout=${executionResult.stdOut}, stderr=${executionResult.stdErr})"
    }
  }

}
