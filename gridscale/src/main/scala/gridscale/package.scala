import cats.data.Kleisli

import scala.language.{ higherKinds, postfixOps }

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

  //  implicit class MonadDecorator[M[_]: Monad, A](m: M[A]) {
  //    def until(end: M[Boolean]): M[A] = until(Kleisli[M, A, Boolean](_ ⇒ end))
  //    def until(end: A ⇒ Boolean): M[A] = until(Kleisli[M, A, Boolean](a ⇒ end(a).pure[M]))
  //
  //    def until(end: Kleisli[M, A, Boolean]): M[A] = {
  //      def stop(a: A): Either[Unit, A] = Right(a)
  //      val continue: Either[Unit, A] = Left(Unit)
  //
  //      def loop = Monad[M].tailRecM[Unit, A](Unit) { i ⇒
  //        val comp =
  //          for {
  //            a ← m
  //            b ← end.run(a)
  //          } yield (b, a)
  //
  //        comp.map { case (e, a) ⇒ (if (e) stop(a) else continue) }
  //      }
  //
  //      loop
  //    }
  //
  //    //    def repeat(size: Int): M[Vector[A]] = {
  //    //      type Rec = (List[A], Int)
  //    //
  //    //      def stop(a: List[A]): Either[Rec, List[A]] = Right(a)
  //    //      def continue(a: List[A], size: Int): Either[Rec, List[A]] = Left((a, size))
  //    //
  //    //      def loop = Monad[M].tailRecM[Rec, List[A]]((List.empty, 0)) {
  //    //        case (list, s) =>
  //    //          if (s < size) m.map(a => continue(a :: list, s + 1))
  //    //          else stop(list).pure[M]
  //    //      }
  //    //
  //    //      loop.map { _.reverse.toVector }
  //    //    }
  //
  //  }

  def waitUntilEnded[M[_]: Monad](f: M[JobState], wait: Time = 10 seconds)(implicit system: System[M]) = {
    def pull = for {
      s ← f
      end = JobState.isFinal(s)
      _ ← if (!end) system.sleep(wait) else ().pure[M]
    } yield (end, s)

    pull.until(_._1).map(_._2)
  }

  case class ExecutionResult(returnCode: Int, stdOut: String, stdErr: String)

  object ExecutionResult {
    def error(command: String, executionResult: ExecutionResult) = {
      import executionResult._
      s"Unexpected return code $returnCode, when running $command (stdout=${executionResult.stdOut}, stderr=${executionResult.stdErr})"
    }
  }

  object RemotePath {
    def child(parent: String, child: String) = if (parent.endsWith("/")) parent + child else parent + '/' + child

    def parent(path: String): Option[String] = {
      val cleaned = path.reverse.dropWhile(c ⇒ c == '/' || c == '\\').reverse
      cleaned match {
        case "" ⇒ None
        case _  ⇒ Some(cleaned.dropRight(name(path).length))
      }
    }

    def name(path: String) = new java.io.File(path).getName
  }

}