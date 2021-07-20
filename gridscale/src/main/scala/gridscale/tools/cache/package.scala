package gridscale.tools

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.{ Lock, ReentrantLock }

import scala.collection.mutable.HashMap

package object cache {

  case class DisposableCache[T](f: () ⇒ T) {
    private var cached: Option[T] = None

    def get: T = synchronized {
      cached match {
        case None ⇒
          val res = f()
          cached = Some(res)
          res
        case Some(t) ⇒ t
      }
    }

    def map[A](f: T ⇒ A): Option[A] = synchronized(cached.map(f))
    def foreach[A](f: T ⇒ A): Unit = synchronized(cached.foreach[A](f))
    def dispose: Unit = synchronized { cached = None }
  }

  case class KeyValueCache[K, V](f: K ⇒ V) {
    val locks = new LockRepository[K]()
    val cached = new collection.mutable.HashMap[K, V]()

    def get(k: K): V = locks.withLock(k) {
      value(k) match {
        case Some(v) ⇒ v
        case None    ⇒ insert(k, f(k))
      }
    }

    def getValidOrInvalidate(k: K, valid: V ⇒ Boolean, clean: V ⇒ Unit = _ ⇒ ()): V = locks.withLock(k) {
      value(k) match {
        case Some(v) ⇒
          if (valid(v)) v
          else {
            clean(v)
            remove(k)
            insert(k, f(k))
          }
        case None ⇒ insert(k, f(k))
      }
    }

    def values: Vector[V] = cached.synchronized(cached.values.toVector)

    private def insert(k: K, v: V) = cached.synchronized { cached.put(k, v); v }
    private def value(k: K) = cached.synchronized(cached.get(k))
    private def contains(k: K) = cached.synchronized(cached.contains(k))
    private def remove(k: K) = cached.synchronized { cached.remove(k) }

    def clear(): Unit = {
      locks.clear()
      cached.clear()
    }
  }

  class LockRepository[T] {
    val locks = new HashMap[T, (Lock, AtomicInteger)]

    def clear() = locks.clear

    def nbLocked(k: T): Int = synchronized(locks.get(k).map {
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

    def withLock[A](obj: T)(op: ⇒ A): A = {
      lock(obj)
      try op
      finally unlock(obj)
    }
  }

}
