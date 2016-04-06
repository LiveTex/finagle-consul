package com.github.dmexe.finagle.consul.integration

import com.twitter.finagle.http.{Method, Request, Response, Status}
import com.twitter.finagle.{Http, ListeningServer, Service}
import com.twitter.util.{Await, Future}

import com.github.dmexe.finagle.consul.Spec

class E2ESpec extends Spec {

  "E2E" should {
    "servers and client communication" in Deferable { defer =>

      val service0 = new Service[Request, Response] {
        def apply(req: Request) = Future.value(Response(req.version, Status.Ok))
      }

      val server0 = Http.serveAndAnnounce("consul!localhost:8500!/E2ESpec", service0)
      defer(server0.close())

      val server1 = Http.serveAndAnnounce("consul!localhost:8500!/E2ESpec", service0)
      defer(server1.close())

      Thread.sleep(2000)

      val httpClient = Http.newService("consul!localhost:8500!/E2ESpec?ttl=1")
      defer(httpClient.close())

      val req = Request(Method.Get, "/")

      // live: 0,1
      Await.result(httpClient(req))

      // live 1
      server0.close()

      Thread.sleep(2000)
      val server2 = Http.serveAndAnnounce("consul!localhost:8500!/E2ESpec", service0)
      defer(server2.close())
      Thread.sleep(2000)

      // live 0,2
      Await.result(httpClient(req))
      // live 2
      server1.close()

      Thread.sleep(2000)
      val server3 = Http.serveAndAnnounce("consul!localhost:8500!/E2ESpec", service0)
      defer(server3.close())
      Thread.sleep(2000)

      // live 2,3
      Await.result(httpClient(req))
      Thread.sleep(1000)
    }
  }
}
