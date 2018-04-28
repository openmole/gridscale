package gridscale.tools

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.{ Lock, ReentrantLock }

import scala.collection.mutable.HashMap

package object cache {

  case class DisposableCache[T](f: () ⇒ T) {
    private var cached: Option[T] = None

    def get = synchronized {
      cached match {
        case None ⇒
          val res = f()
          cached = Some(res)
          res
        case Some(t) ⇒ t
      }
    }

    def map[A](f: T ⇒ A) = synchronized(cached.map(f))
    def foreach[A](f: T ⇒ A) = synchronized(cached.foreach[A](f))
    def dispose = synchronized(cached = None)
  }

  case class KeyValueCache[K, V](f: K ⇒ V) {
    val locks = new LockRepository[K]()
    val cached = new collection.mutable.HashMap[K, V]()

    def get(k: K) = locks.withLock(k) {
      value(k) match {
        case Some(v) ⇒ v
        case None    ⇒ insert(k, f(k))
      }
    }

    def values = cached.synchronized(cached.values.toVector)

    private def insert(k: K, v: V) = cached.synchronized { cached.put(k, v); v }
    private def value(k: K) = cached.synchronized(cached.get(k))
    private def contains(k: K) = cached.synchronized(cached.contains(k))

    def clear() = {
      locks.clear()
      cached.clear()
    }
  }

  class LockRepository[T] {
    val locks = new HashMap[T, (Lock, AtomicInteger)]

    def clear() = locks.clear

    def nbLocked(k: T) = synchronized(locks.get(k).map {
      _._2.get
    }.getOrElse(0))

    private def lock(obj: T) = synchronized {
      val lock = locks.getOrElseUpdate(obj, (new ReentrantLock, new AtomicInteger(0)))
      lock._2.incrementAndGet
      lock._1
    }.lock

    private def unlock(obj: T): Unit = synchronized {
      locks.get(obj).foreach { lock ⇒
        val value = lock._2.decrementAndGet
        if (value <= 0) locks.remove(obj)
        lock._1.unlock
      }
    }

    def withLock[A](obj: T)(op: ⇒ A) = {
      lock(obj)
      try op
      finally unlock(obj)
    }
  }

}
