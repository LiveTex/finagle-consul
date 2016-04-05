package com.github.dmexe.finagle.consul

import com.twitter.finagle.Http
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers, WordSpecLike}

class ConsulSessionSpec extends FlatSpec with Matchers with BeforeAndAfterAll {

  val client = Http.newService("localhost:8500")

  override def afterAll: Unit = {
    client.close()
  }

  "ConsulSession" should "open/close" in {
    val session = new ConsulSession(client, ConsulSession.Options("spec", ttl = 10, interval = 1, lockDelay = 1))

    try {
      Thread.sleep(2000)
      val Some(reply) = session.get
      assert(reply.nonEmpty)
      session.close()
    } finally {
      session.close()
    }
  }

  "ConsulSession" should "heartbeat lost" in {
    val session = new ConsulSession(client, ConsulSession.Options("spec", ttl = 10, interval = 20, lockDelay = 1))

    try {
      Thread.sleep(3000)
      val Some(id0) = session.get
      assert(id0.nonEmpty)

      Thread.sleep(20000)
      val Some(id1) = session.get
      assert(id1.nonEmpty)
      assert(id1 != id0)

    } finally {
      session.close()
    }
  }
}
