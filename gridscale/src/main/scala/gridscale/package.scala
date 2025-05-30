
import squants.time.*

package object gridscale {

  enum FileType:
    case Directory, File, Link, Unknown

  case class ListEntry(name: String, `type`: FileType, modificationTime: Option[Time] = None)

  object JobState:
    extension (s: JobState)
      def isFinal: Boolean =
        s match
          case Done | _: Failed =>  true
          case _ => false
      
  enum JobState:
    case Submitted, Running, Done
    case Failed(reason: String = "")

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

  def waitUntilEnded(f: () => JobState, wait: Time = 10 seconds): JobState =
    def pull: JobState =
      val s = f()
      val continue = !JobState.isFinal(s)
      if continue
      then
        Thread.sleep(wait.toMillis)
        pull
      else s

    pull

  case class ExecutionResult(returnCode: Int, stdOut: String, stdErr: String)

  object ExecutionResult {
    def error(suggestionMessage: String = "")(command: String, executionResult: ExecutionResult): String = {
      import executionResult._
      s"Unexpected return code $returnCode, when running $command (stdout=${executionResult.stdOut}, stderr=${executionResult.stdErr}).\n$suggestionMessage"
    }

    def error(command: String, executionResult: ExecutionResult): String = error("")(command, executionResult)
  }

  object RemotePath {
    def child(parent: String, child: String): String =
      if (parent.isEmpty) child
      else if (parent.endsWith("/")) parent + child
      else parent + '/' + child

    def parent(path: String): Option[String] = {
      val cleaned = path.reverse.dropWhile(c ⇒ c == '/' || c == '\\').reverse
      cleaned match {
        case "" ⇒ None
        case _  ⇒ Some(cleaned.dropRight(name(path).length))
      }
    }

    def name(path: String): String = new java.io.File(path).getName
  }

}
