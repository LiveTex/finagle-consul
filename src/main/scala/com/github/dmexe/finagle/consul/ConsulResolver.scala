package com.github.dmexe.finagle.consul

import java.net.{InetSocketAddress, SocketAddress}
import java.util.logging.Logger

import com.twitter.finagle.util.DefaultTimer
import com.twitter.finagle.{Addr, Resolver, Address}
import com.twitter.util.Var

class ConsulResolver extends Resolver {
  val scheme = "consul"

  private val log        = Logger.getLogger(getClass.getName)
  private val timer      = DefaultTimer.twitter
  private var digest     = ""

  private def addresses(hosts: String, name: String) : Option[Set[Address]] = {
    val services  = ConsulService.get(hosts).list(name)
    val newDigest = services.map(_.ID).sorted.mkString(",")
    if (newDigest != digest) {
      val newAddrs = services.map{ s =>
        Address(s.Address, s.Port)
      }.toSet
      log.info(s"Consul resolver addresses=$newAddrs")
      digest = newDigest
      Some(newAddrs)
    } else {
      None
    }
  }

  def addrOf(hosts: String, query: ConsulQuery): Var[Addr] =
    Var.async(Addr.Pending: Addr) { u =>
      val maybeAddrs = addresses(hosts, query.name)
      maybeAddrs foreach { addrs =>
        u() = Addr.Bound(addrs)
      }

      timer.schedule(query.ttl.fromNow, query.ttl) {
        addresses(hosts, query.name) foreach { addrs =>
          u() = Addr.Bound(addrs)
        }
      }
    }

  def bind(arg: String): Var[Addr] = arg.split("!") match {
    case Array(hosts, query) =>
      ConsulQuery.decodeString(query) match {
        case Some(q) => addrOf(hosts, q)
        case None =>
          throw new IllegalArgumentException(s"Invalid address '$arg'")
      }

    case _ =>
      throw new IllegalArgumentException(s"Invalid address '$arg'")
  }
}
