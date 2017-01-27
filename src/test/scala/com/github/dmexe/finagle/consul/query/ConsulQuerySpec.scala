package com.github.dmexe.finagle.consul.query

import java.net.InetSocketAddress

import com.github.dmexe.finagle.consul.{ ConsulQuery, Spec }

class ConsulQuerySpec extends Spec {

  "ConsulQuery" should {

    "parse values" in {
      val Some(ConsulQuery(name, ttl, tags, dc, proxy)) =
        ConsulQuery.decodeString("/name?dc=DC&ttl=45&proxy=127.0.0.1:8500&tag=prod&tag=trace")
      assert(name          == "name")
      assert(ttl.toString  == "45.seconds")
      assert(tags          == Set("prod", "trace"))
      assert(dc            == Some("DC"))
      assert(proxy         == Some(new InetSocketAddress("127.0.0.1", 8500)))
    }

    "parse tags with equal symbol" in {
      val Some(ConsulQuery(_, _, tags, _, _)) =
        ConsulQuery.decodeString("/name?tag=circuit=prod&tag=version=1.2.3")

      assert(tags          == Set("circuit=prod", "version=1.2.3"))
    }

    "parse empty string" in {
      val Some(ConsulQuery(name, ttl, tags, dc, proxy)) = ConsulQuery.decodeString("")
      assert(name          == "")
      assert(ttl.toString  == "10.seconds")
      assert(tags          == Set())
      assert(dc.isEmpty)
      assert(proxy.isEmpty)
    }
  }
}
