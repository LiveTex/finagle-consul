package com.github.dmexe.finagle.consul

import scala.concurrent.duration._

/**
 * Create finagle dest for consul.
 *
 * Example:
 * {{{
 *   val str = ConsulServiceDest("localhost", 2000, "MyService").toString
 *   assert(str == "consul!localhost:200!/MyService?ttl=10&tag=circuit=test")
 * }}}
 * @param host
 * @param port
 * @param serviceId
 * @param circuit
 * @param version
 * @param ttl
 */
case class ConsulServiceDest(
  host: String,
  port: Int,
  serviceId: String,
  circuit: Option[String],
  version: Option[String] = None,
  ttl: FiniteDuration = 10.seconds
) {
  override def toString: String = {
    s"consul!${host}:${port}!/$serviceId?ttl=${ttl.toSeconds}" +
      s"${circuit.fold("")(c => s"&tag=circuit=$c")}" +
      s"${version.fold("")(v => s"&tag=version=$v")}"
  }
}
