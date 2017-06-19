package com.github.dmexe.finagle.consul

import com.github.dmexe.finagle.consul.client.{ HttpClientFactory, KeyService }
import com.github.dmexe.finagle.consul.ConsulSession.{ Options => SessionOptions }
import com.twitter.finagle.{ Service, http }
import com.twitter.util._
import org.slf4j.LoggerFactory

class ConsulLock(lockName: String, httpClient: Service[http.Request,http.Response], opts: Option[SessionOptions])
  extends java.io.Closeable {

  private var sessionId   = Option.empty[String]
  private val log         = LoggerFactory.getLogger(getClass)
  private val lockKey     = s"finagle/lock/$lockName"
  private val kv          = new KeyService(httpClient)
  private val sessionOpts = opts.getOrElse(SessionOptions(name = lockName)).copy(handler = handle)
  private val session     = new ConsulSession(httpClient, sessionOpts)

  def close(): Unit = session.close()

  def tryLock(fn: => Unit): Boolean = {
    sessionId match {
      case Some(id) if acquireLock(id) =>
        try {
          fn
          true
        } finally releaseLock(id)
      case _ =>
        false
    }
  }

  private[consul] def handle(id: String): Unit = {
    sessionId = Some(id)
  }

  private def acquireLock(sessionId: String): Boolean = {
    val reply = kv.acquire(lockKey, sessionId, "") rescue {
      case NonFatal(e) =>
        log.error(e, e.getMessage)
        Future.value(false)
    } map { v =>
      if (v) log.info(s"Lock acquired lock=$lockName id=$sessionId")
      v
    }
    Await.result(reply)
  }

  private def releaseLock(sessionId: String): Unit = {
    val reply = kv.release(lockKey, sessionId) map {
      case true =>
        log.info(s"Lock released lock=$lockName id=$sessionId")
      case false =>
        log.warning(s"Fail to release lock, locked by another session lock=$lockName id=$session")
    }
    Await.result(reply)
  }
}

object ConsulLock {
  def get(lockName: String, hosts: String): ConsulLock = {
    val client  = HttpClientFactory.getClient(hosts)
    new ConsulLock(lockName, client, None)
  }
}
