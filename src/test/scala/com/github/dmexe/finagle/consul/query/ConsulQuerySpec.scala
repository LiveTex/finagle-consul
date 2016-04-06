package com.github.dmexe.finagle.consul.query

import com.github.dmexe.finagle.consul.{ConsulQuery, Spec}

class ConsulQuerySpec extends Spec {

  "ConsulQuery" should {

    "parse values" in {
      val Some(ConsulQuery(name, ttl, tags, dc)) = ConsulQuery.decodeString("/name?dc=DC&ttl=45&tag=prod&tag=trace")
      assert(name          == "name")
      assert(ttl.toString  == "45.seconds")
      assert(tags          == Set("prod", "trace"))
      assert(dc            == Some("DC"))
    }

    "parse empty string" in {
      val Some(ConsulQuery(name, ttl, tags, dc)) = ConsulQuery.decodeString("")
      assert(name          == "")
      assert(ttl.toString  == "10.seconds")
      assert(tags          == Set())
      assert(dc.isEmpty)
    }
  }
}
