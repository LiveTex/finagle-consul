package com.github.dmexe.finagle.consul

import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

import com.github.dmexe.finagle.consul.client.HttpErrors.KeyNotFoundError
import com.github.dmexe.finagle.consul.client.{AgentService, HttpClientFactory}
import com.twitter.finagle.util.DefaultTimer
import com.twitter.finagle.{Announcement, Announcer}
import com.twitter.logging.Logger
import com.twitter.util.{Await, Duration, Future}

object ConsulAnnouncer {
  def badAnnouncement(addr: String): Future[Announcement] = {
    Future.exception(new IllegalArgumentException(s"Invalid addr '$addr'"))
  }
}

class ConsulAnnouncer extends Announcer {

  import ConsulAnnouncer._

  override val scheme: String = "consul"

  private val timer         = DefaultTimer.twitter
  private val log           = Logger.get(getClass)
  val maxHeartbeatFrequency = Duration(10, TimeUnit.SECONDS)

  def announce(ia: InetSocketAddress, hosts: String, q: ConsulQuery): Future[Announcement] = {
    val freq   = q.ttl / 2
    require(freq.inSeconds >= 1, "Service TTL must be above two seconds!")

    val agent  = new AgentService(HttpClientFactory.getClient(hosts))
    val regReq = agent.mkServiceRequest(ia, q)
    val reply  = agent.registerService(regReq) flatMap { _ => agent.passHealthCheck(regReq.checkId) }

    reply map { checkId =>
      log.debug(s"Successfully registered consul service: ${regReq.id}")

      val heartbeatFrequency = freq.min(maxHeartbeatFrequency)
      log.debug(s"Heartbeat frequency: $heartbeatFrequency")

      val heartbeatTask = timer.schedule(heartbeatFrequency) {
        log.trace("heartbeat tick")

        val reply = agent.passHealthCheck(checkId) rescue {
          // Avoid net split desync
          case e: KeyNotFoundError => {
            log.warning(s"Health check $checkId was not found, try to register service ${regReq.id}")
            agent.registerService(regReq)
          }
        }
        Await.result(reply)
      }

      new Announcement {
        override def unannounce(): Future[Unit] = {
          // sequence stopping the heartbeat and deleting the service registration
          for {
            _ <- heartbeatTask.close()
            _ <- agent.deregisterService(regReq.id)
          } yield {}
        }
      }
    }
  }

  override def announce(ia: InetSocketAddress, addr: String): Future[Announcement] = {
    addr.split("!") match {
      case Array(hosts, query) =>
        ConsulQuery.decodeString(query) match {
          case Some(q) => announce(ia, hosts, q)
          case None    => badAnnouncement(addr)
        }
      case _ => badAnnouncement(addr)
    }
  }
}
