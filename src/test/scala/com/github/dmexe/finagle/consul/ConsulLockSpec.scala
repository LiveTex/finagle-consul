package com.github.dmexe.finagle.consul

import com.github.dmexe.finagle.consul.client.HttpClientFactory
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}

class ConsulLockSpec extends FlatSpec with Matchers with BeforeAndAfterAll {

  import ConsulLock.Status._

  "ConsulLock" should "lock/unlock" in {
    val client   = HttpClientFactory.getClient("localhost:8500")
    val opts     = ConsulSession.Options(name = "test", ttl = 10, interval = 1, lockDelay = 10)

    val lock0 = new ConsulLock("spec", client, Some(opts))
    Thread.sleep(2000)

    val lock1 = new ConsulLock("spec", client, Some(opts))
    Thread.sleep(2000)

    try {
      assert(lock0.get == Leader)
      assert(lock1.get == Follower)

      lock0.close()

      Thread.sleep(2000)

      assert(lock1.get == Leader)
    } finally {
      lock0.close()
      lock1.close()
      client.close()
    }
  }
}
