package com.github.dmexe.finagle.consul

import com.github.dmexe.finagle.consul.client.{AgentService, HttpClientFactory}
import com.twitter.logging.Logger
import com.twitter.finagle.util.DefaultTimer
import com.twitter.finagle.{Addr, Address, Resolver}
import com.twitter.util.{Await, Var}

object ConsulResolver {
  final case class InvalidAddressError(addr: String) extends IllegalArgumentException(s"Invalid address '$addr'")
}

class ConsulResolver extends Resolver {

  import ConsulResolver._

  val scheme = "consul"

  private val log    = Logger.get(getClass)
  private val timer  = DefaultTimer.twitter
  private var digest = Seq.empty[String]

  private def addresses(agent: AgentService, q: ConsulQuery) : Seq[Address] = {
    val services  = Await.result(agent.getHealthServices(q)) map (_.service)
    val newDigest = services.map{s => s"${s.address}:${s.port}"}.sorted

    if (digest != newDigest) {
      digest = newDigest
      log.debug(s"Consul catalog lookup, addresses: $newDigest")
    }

    services map { service =>
      Address(service.address, service.port)
    }
  }

  def addrOf(hosts: String, query: ConsulQuery): Var[Addr] =
    Var.async(Addr.Pending: Addr) { u =>
      val client = HttpClientFactory.getClient(hosts)
      val agent  = new AgentService(client)

      u() = Addr.Bound(addresses(agent, query).toSet)

      timer.schedule(query.ttl) {
        val addrs = addresses(agent, query).toSet
        if (addrs.nonEmpty) {
          u() = Addr.Bound(addrs)
        }
      }
    }

  def bind(arg: String): Var[Addr] = arg.split("!") match {
    case Array(hosts, query) =>
      ConsulQuery.decodeString(query) match {
        case Some(q) => addrOf(hosts, q)
        case None    => throw new InvalidAddressError(arg)
      }
    case _ =>
      throw new InvalidAddressError(arg)
  }
}
