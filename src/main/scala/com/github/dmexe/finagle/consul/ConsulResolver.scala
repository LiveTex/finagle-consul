package com.github.dmexe.finagle.consul

import com.github.dmexe.finagle.consul.client.{ AgentService, HttpClientFactory }
import com.twitter.finagle.{ Addr, Address, Resolver }
import com.twitter.util.{ Future, _ }
import org.slf4j.LoggerFactory

object ConsulResolver {
  final case class InvalidAddressError(addr: String) extends IllegalArgumentException(s"Invalid address '$addr'")
}

class ConsulResolver extends Resolver {

  import ConsulResolver._

  val scheme = "consul"

  private val log    = LoggerFactory.getLogger(getClass)
  private val timer  = new JavaTimer(isDaemon = true, Some("ConsulResolver-timer"))

  private def addresses(agent: AgentService, q: ConsulQuery) : Set[Address] = {
    val services = Await.result(agent.getHealthServices(q)).map(_.service)
    services
      .map(service => Address(service.address, service.port))
      .toSet
  }

  private def resolve(addrs: Set[Address]): Addr = {
    if (addrs.isEmpty) {
      Addr.Neg
    } else {
      Addr.Bound(addrs)
    }
  }

  def addrOf(hosts: String, query: ConsulQuery): Var[Addr] = {
    Var.async(Addr.Pending: Addr) { u =>
      val client = HttpClientFactory.getClient(hosts)
      val agent = new AgentService(client)
      var curAddr = resolve(addresses(agent, query))
      u.update(curAddr)

      val updateTask = timer.schedule(query.ttl) {
        val newAddr = resolve(addresses(agent, query))
        if (curAddr != newAddr) {
          curAddr = newAddr
          log.info(s"Consul catalog lookup, service: ${query.name}, addresses: $newAddr")
          u.update(curAddr)
        }
      }
      new Closable {
        override def close(deadline: Time): Future[Unit] = {
          Closable.sequence(updateTask, client).close(deadline)
        }
      }
    }
  }

  def bind(arg: String): Var[Addr] = arg.split("!") match {
    case Array(hosts, query) =>
      ConsulQuery.decodeString(query) match {
        case Some(q) =>
          addrOf(hosts, q).map { addr =>
            log.debug(s"Bind ${q.name} to $addr")
            addr
          }
        case None    => throw new InvalidAddressError(arg)
      }
    case _ =>
      throw new InvalidAddressError(arg)
  }
}
