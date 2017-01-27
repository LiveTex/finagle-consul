package com.github.dmexe.finagle.consul.examples

import com.twitter.finagle.Http
import com.twitter.finagle.http.Request
import com.twitter.util.Await

object EchoPinger {
  def main(args: Array[String]): Unit = {
    val cli = Http.client
      .newService("consul!192.168.4.14:3004!/EchoServer?ttl=2")
    (1 to 100).foreach { v =>
      Thread.sleep(100)
      val req = Request()
      req.setContentString(v.toString)
      println(Await.result(cli(req)).getContentString())
    }
  }
}
