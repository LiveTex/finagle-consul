package com.github.dmexe.finagle.consul

import java.util.concurrent.TimeUnit
import java.util.logging.{Level, Logger}

import com.github.dmexe.finagle.consul.client.{SessionService,HttpClientFactory}
import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response}
import com.twitter.util._
import com.twitter.finagle.util.DefaultTimer

class ConsulSession(httpClient: Service[Request, Response], opts: ConsulSession.Options) {

  import ConsulSession._

  val log   = Logger.getLogger(getClass.getName)
  val timer = DefaultTimer.twitter

  private[consul] var sessionId     = Option.empty[String]
  private[this]   var listeners     = List.empty[Listener]
  private[this]   val client        = SessionService(httpClient)
  private[this]   var timerTask     = Option.empty[TimerTask]

  def start(): Unit = {
    timerTask.getOrElse {
      val task = timer.schedule(Duration(0, TimeUnit.SECONDS).fromNow, Duration(opts.interval, TimeUnit.SECONDS)) {
        log.fine("Consul heartbeat tick")
        if (!isOpen) { open() }
        renew()
        tickListeners()
      }
      timerTask = Some(task)
    }
  }

  def stop(): Unit = {
    timerTask foreach { task =>
      val fu = task.close()
      Await.result(fu)
      close()
      timerTask = None
    }
  }

  def isOpen = sessionId.isDefined

  def info(): Option[SessionService.SessionResponse] = {
    sessionId flatMap infoReq
  }

  def addListener(listener: Listener): Unit = {
    synchronized {
      listeners = listeners ++ List(listener)
    }
    sessionId foreach listener.start
  }

  def delListener(listener: Listener): Unit = {
    synchronized {
      listeners = listeners.filterNot(_ == listener)
    }
  }

  private[consul] def renew(): Unit = {
    sessionId foreach { sid =>
      val reply = renewReq(sid)
      if(reply.isEmpty) {
        log.info(s"Consul session not found id=$sid")
        close()
      }
    }
  }

  private[consul] def tickListeners(): Unit = {
    sessionId foreach { sid =>
      listeners foreach { listener =>
        muted(listener.tick(sid))
      }
    }
  }

  private[consul] def open(): Unit = {
    synchronized {
      sessionId getOrElse {
        val reply = createReq()
        log.info(s"Consul session created id=${reply.ID}")
        listeners foreach { l => muted(l.start(reply.ID)) }
        sessionId = Some(reply.ID)
      }
    }
  }

  private[consul] def close(): Unit = {
    synchronized {
      if (sessionId.isDefined) {
        sessionId foreach { id =>
          listeners foreach { l => muted(l.stop(id)) }
          muted(destroyReq(id))
          log.info(s"Consul session removed id=$id")
        }
        sessionId = None
      }
    }
  }

  private def muted(f: => Unit): Unit = {
    try {
      f
    } catch {
      case NonFatal(e) =>
        log.log(Level.SEVERE, e.getMessage, e)
    }
  }

  private def createReq() = {
    val createRequest = SessionService.CreateRequest(
      LockDelay = s"${opts.lockDelay}s", Name = opts.name, Behavior = "delete", TTL = s"${opts.ttl}s"
    )
    Await.result(client.create(createRequest))
  }

  private def destroyReq(session: String): Unit = {
    Await.result(client.destroy(session))
  }

  private def renewReq(session: String): Option[SessionService.SessionResponse] = {
    Await.result(client.renew(session))
  }

  private def infoReq(session: String): Option[SessionService.SessionResponse] = {
    Await.result(client.info(session))
  }
}

object ConsulSession {

  val SESSION_HEARTBEAT_COOLDOWN = 3000

  trait Listener {
    def start(session: String) : Unit
    def stop(session:  String) : Unit
    def tick(session:  String) : Unit = {}
  }

  case class Options(name: String, ttl: Int = 45, interval: Int = 20, lockDelay: Int = 10)

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
