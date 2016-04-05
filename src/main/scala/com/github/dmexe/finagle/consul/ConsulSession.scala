package com.github.dmexe.finagle.consul

import java.util.concurrent.TimeUnit

import com.github.dmexe.finagle.consul.client.{SessionService,HttpClientFactory}
import com.twitter.finagle.Service
import com.twitter.finagle.http
import com.twitter.util._
import com.twitter.finagle.util.DefaultTimer
import com.twitter.logging.Logger

class ConsulSession(httpClient: Service[http.Request, http.Response], opts: ConsulSession.Options) {

  val log     = Logger.get(getClass)
  val timer   = DefaultTimer.twitter

  private var sessionId     = Option.empty[String]
  private val sessionClient = new SessionService(httpClient)
  private val interval      = Duration(opts.interval, TimeUnit.SECONDS)
  private val timerTask     = timer.schedule(Duration(1, TimeUnit.SECONDS).fromNow, interval) { timerTick() }

  def close(): Unit = {
    Await.result(timerTask.close())
    sessionId foreach { id =>
      Await.result(sessionClient.destroy(id))
      log.info(s"Session successfuly removed id=$id")
    }
  }

  private[consul] def get  = sessionId
  private[consul] def task = timerTask

  private def timerTick(): Unit = {
    log.trace("timer tick")

    val reply = getOrCreateSession(sessionId) flatMap renewSession map keepSession map opts.handler
    Await.result(reply)
  }

  private def keepSession(id: String): String = {
    synchronized { sessionId = Some(id) }
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
  val idHandler = (id:String) => ()

  final case class Options(
    name:      String,
    handler:   Handler = idHandler,
    ttl:       Int = 45,
    interval:  Int = 20,
    lockDelay: Int = 10
  )

  val defaultSessionOptions = ConsulSession.Options(name = "finagle.default")

  def get(hosts: String, opts: ConsulSession.Options): ConsulSession = {
    val newSession = new ConsulSession(
      HttpClientFactory.getClient(hosts),
      opts
    )
    newSession
  }

  def get(hosts: String): ConsulSession = get(hosts, defaultSessionOptions)
}
