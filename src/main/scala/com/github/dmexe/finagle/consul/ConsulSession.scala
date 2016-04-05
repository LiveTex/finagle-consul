package com.github.dmexe.finagle.consul

import java.util.concurrent.TimeUnit

import com.github.dmexe.finagle.consul.client.SessionService
import com.twitter.finagle.{Service, http}
import com.twitter.finagle.util.DefaultTimer
import com.twitter.logging.Logger
import com.twitter.util._

class ConsulSession(httpClient: Service[http.Request, http.Response], opts: ConsulSession.Options) {

  val log   = Logger.get(getClass)
  val timer = DefaultTimer.twitter

  private var sessionId     = Option.empty[String]
  private val sessionClient = new SessionService(httpClient)
  private val interval      = Duration(opts.interval, TimeUnit.SECONDS)
  private val timerTask     = timer.schedule(Duration(0, TimeUnit.SECONDS).fromNow, interval) { timerTick() }

  def close(): Unit = synchronized {
    Await.result(timerTask.close())
    sessionId foreach { id =>
      opts.cleanup(id)

      Await.result(sessionClient.destroy(id))
      log.info(s"Session successfuly removed id=$id")
      sessionId = None
    }
  }

  private[consul] def get = sessionId

  private def timerTick(): Unit = {
    log.trace("timer tick")

    val id = Await.result(getOrCreateSession(sessionId) flatMap renewSession map keepSession)
    opts.handler(id)
  }

  private def keepSession(id: String): String = synchronized {
    sessionId = Some(id)
    id
  }

  private def renewSession(id: String): Future[String] = {
    sessionClient.renew(id) flatMap {
      case Some(value) => Future.value(value.id)
      case None        =>
        log.info(s"Session id=$id lost")
        getOrCreateSession(None)
    }
  }

  private def getOrCreateSession(id: Option[String]): Future[String] = {
    id match {
      case Some(value) => Future.value(value)
      case None        => createSession()
    }
  }

  private def createSession(): Future[String] = {
    sessionClient.create(name = opts.name, lockDelay = opts.lockDelay, ttl = opts.ttl) map sessionCreated
  }

  private def sessionCreated(id: String): String = {
    log.info(s"New session successfully created id=$id")
    id
  }
}

object ConsulSession {

  type Handler  = (String) => Unit
  val idHandler = (_:String) => ()

  final case class Options(
    name:      String,
    handler:   Handler = idHandler,
    cleanup:   Handler = idHandler,
    ttl:       Int     = 30,
    interval:  Int     = 15,
    lockDelay: Int     = 10
  )
}
