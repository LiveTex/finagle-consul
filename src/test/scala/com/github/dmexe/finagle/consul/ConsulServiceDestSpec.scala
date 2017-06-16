package com.github.dmexe.finagle.consul

import org.scalatest.FreeSpec

class ConsulServiceDestSpec extends FreeSpec {

  "should create correct dest string" in {
    val str = ConsulServiceDest("localhost", 200, "MyService", Some("test")).toString
    assert(str == "consul!localhost:200!/MyService?ttl=10&tag=circuit=test")
  }
}
