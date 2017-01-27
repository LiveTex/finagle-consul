package com.github.dmexe.finagle.consul

import java.net.InetSocketAddress

import com.twitter.util.{ Duration, Try }
import org.jboss.netty.handler.codec.http.QueryStringDecoder
import scala.collection.JavaConverters._

case class ConsulQuery(
  name:   String,
  ttl:    Duration,
  tags:   Set[String],
  dc:     Option[String],
  proxy:  Option[InetSocketAddress]
)

object ConsulQuery {

  def decodeString(query: String): Option[ConsulQuery] = {
    val q      = new QueryStringDecoder(query)
    val name   = q.getPath.stripPrefix("/").split("/") mkString "."
    val params = q.getParameters.asScala
    val ttl    = params.get("ttl").map(readTTL).getOrElse(Duration.fromSeconds(10))
    val tags   = params.get("tag").map(_.asScala.toSet).getOrElse(Set.empty[String])
    val dc     = params.get("dc").map(_.get(0))
    val proxy  = params.get("proxy").flatMap(readProxyAddress)
    Some(ConsulQuery(name, ttl, tags, dc, proxy))
  }

  private def readTTL(ttls: java.util.List[String]): Duration =
    Duration.fromSeconds(ttls.get(0).toInt)

  private def readProxyAddress(addrs: java.util.List[String]): Option[InetSocketAddress] = {
    Try({
      val hostPort = addrs.get(0).split(':')
      new InetSocketAddress(hostPort.head, hostPort.last.toInt)
    }).toOption
  }
}
