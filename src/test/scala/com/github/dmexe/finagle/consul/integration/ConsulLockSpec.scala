package com.github.dmexe.finagle.consul.integration

import com.twitter.finagle.Http
import com.github.dmexe.finagle.consul.{ConsulLock, ConsulSession, Spec}

class ConsulLockSpec extends Spec {

  "ConsulLock" should {

    "lock/unlock key" in Deferable { defer =>

      val opts  = ConsulSession.Options(name = "test", ttl = 10, interval = 1, lockDelay = 10)

      val lock0 = new ConsulLock("spec", httpClient, Some(opts))
      defer(lock0.close())
      Thread.sleep(2000)

      val lock1 = new ConsulLock("spec", httpClient, Some(opts))
      defer(lock1.close())
      Thread.sleep(2000)

      var re0: Boolean = false
      var re1: Boolean = false

      re0 = lock0.tryLock {
        re1 = lock1.tryLock { () }
      }

      assert(re0)
      assert(!re1)
    }
  }
}
