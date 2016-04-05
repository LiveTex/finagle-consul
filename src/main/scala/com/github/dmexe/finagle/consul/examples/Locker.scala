package com.github.dmexe.finagle.consul.examples

import com.github.dmexe.finagle.consul.ConsulLock

object Locker {
  def main(args: Array[String]): Unit = {

    val ths =
      (1 to 10) map { n =>
        new Thread {
          val lock = ConsulLock.get("example", "localhost:8500")
          override def run(): Unit = {
            var done = false
            while (!done) {
              done = lock.tryLock { println(s"--> n: $n") }
              if (!done) Thread.sleep(1000)
            }
            lock.close()
          }
        }
      }

    ths.foreach(_.start())
    ths.foreach(_.join())
  }
}
