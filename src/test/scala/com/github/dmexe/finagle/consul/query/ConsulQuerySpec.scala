package com.github.dmexe.finagle.consul.query

import com.github.dmexe.finagle.consul.ConsulQuery
import com.github.dmexe.finagle.consul.Spec

class ConsulQuerySpec extends Spec {

  "ConsulQuery" should {

    "parse values" in {
      ConsulQuery.decodeString("/name?dc=DC&ttl=45&tag=prod&tag=trace") match {
        case Some(ConsulQuery(name, ttl, tags, dc)) =>
          assert(name          == "name")
          assert(ttl.toString  == "45.seconds")
          assert(tags          == Set("prod", "trace"))
          assert(dc.contains("DC"))
      }
    }

    "parse empty string" in {
      ConsulQuery.decodeString("") match {
        case Some(ConsulQuery(name, ttl, tags, dc)) =>
          assert(name          == "")
          assert(ttl.toString  == "10.seconds")
          assert(tags          == Set())
          assert(dc.isEmpty)
      }
    }
  }
}
