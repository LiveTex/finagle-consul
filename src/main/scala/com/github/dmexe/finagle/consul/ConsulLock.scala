package com.github.dmexe.finagle.consul

import com.github.dmexe.finagle.consul.client.{HttpClientFactory, KeyService}
import com.github.dmexe.finagle.consul.ConsulSession.{Options => SessionOptions}
import com.twitter.finagle.{Service, http}
import com.twitter.logging.Logger
import com.twitter.util._

class ConsulLock(lockName: String, httpClient: Service[http.Request,http.Response], opts: Option[SessionOptions]) {

  import ConsulLock.Status._

  private val log         = Logger.get(getClass)
  private val lockKey     = s"finagle/lock/$lockName"
  private var status      = Pending
  private val kv          = new KeyService(httpClient)
  private val sessionOpts = opts.getOrElse(SessionOptions(name = lockName)).copy(handler = acquire, cleanup = release)
  private val session     = new ConsulSession(httpClient, sessionOpts)

  def close(): Unit = session.close()
  def get: ConsulLock.Status.Value = status

  private[consul] def acquire(sessionId: String): Unit = {
    val newStatus = Await.result(acquireLock(sessionId))

    if (newStatus != status) {
      logNewStatus(sessionId, newStatus)
    }
    synchronized { status = newStatus }
  }

  private[consul] def release(sessionId: String): Unit = {
    Await.result(releaseLock(sessionId))
  }

  private def logNewStatus(sessionId: String, newStatus: Value): Unit = {
    if (newStatus == Leader) {
      log.info(s"Become a leader lock=$lockName id=$sessionId")
    }
    if (newStatus == Follower) {
      log.info(s"Become a follower lock=$lockName id=$sessionId")
    }
  }

  private def acquireLock(sessionId: String): Future[Value] = {
    val reply = kv.acquire(lockKey, sessionId, "") map {
      case true  => Leader
      case false => Follower
    }

    reply rescue {
      case NonFatal(e) =>
        log.error(e, e.getMessage)
        Future.value(Pending)
    }
  }

  private def releaseLock(sessionId: String): Future[Unit] = {
    if (status != Leader) {
      return Future.value(())
    }

    kv.release(lockKey, sessionId) map {
      case true =>
        log.info(s"Lock released lock=$lockName id=$sessionId")
      case false =>
        log.warning(s"Fail to release lock, locked by another session lock=$lockName id=$session")
    }
  }
}

object ConsulLock {

  object Status extends Enumeration {
    val Pending, Follower, Leader  = Value
  }

  def get(lockName: String, hosts: String): ConsulLock = {
    val client  = HttpClientFactory.getClient(hosts)
    new ConsulLock(lockName, client, None)
  }
}
