
package gridscale

package object effectaside {

  object Effect {
    def apply[T](effect: ⇒ T) = new Effect(effect)
  }

  class Effect[T](effect: ⇒ T) {
    @inline def apply() = effect
  }

  object Random {
    def apply(seed: Long): Effect[Random] = Effect(new Random(new util.Random(seed)))
    def apply(random: util.Random): Effect[Random] = Effect(new Random(random))
  }

  class Random(val random: util.Random) {
    def nextDouble() = random.nextDouble()
    def nextInt(n: Int) = random.nextInt(n)
    def nextBoolean() = random.nextBoolean()
    def shuffle[A](s: Vector[A]) = random.shuffle(s)
    def use[T](f: util.Random ⇒ T) = f(random)
  }

  object Path {
    implicit def stringToPath(path: String): Path = Path(new java.io.File(path))
    implicit def fileToPath(file: java.io.File): Path = Path(file)
  }

  case class Path(path: java.io.File) extends AnyVal {
    override def toString = path.getPath
  }

  object FileSystem {
    def apply() = Effect(new FileSystem())
  }

  class FileSystem {
    def read(path: Path): String = readStream(path)(is ⇒ scala.io.Source.fromInputStream(is).mkString)

    def list(p: Path) = p.path.listFiles.toVector

    def readStream[T](path: Path)(f: java.io.InputStream ⇒ T): T = {
      import java.io._
      val is = new BufferedInputStream(new FileInputStream(path.path))
      try f(is)
      finally is.close
    }

    def writeStream[T](path: Path)(f: java.io.OutputStream ⇒ T) = {
      import java.io._
      val is = new BufferedOutputStream(new FileOutputStream(path.path))
      try f(is)
      finally is.close
    }
  }

  object IO {
    def apply() = Effect(new IO)
  }

  class IO {
    @inline def apply[T](f: () ⇒ T) = f
  }

  object System {
    def apply() = Effect(new System)
  }

  class System {
    def randomUUID() = java.util.UUID.randomUUID()
    def sleep(d: squants.Time) = Thread.sleep(d.millis)
    def currentTime() = java.lang.System.currentTimeMillis()
  }

}
