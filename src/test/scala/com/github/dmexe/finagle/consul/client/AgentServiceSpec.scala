package com.github.dmexe.finagle.consul.client

import java.net.InetSocketAddress

import com.github.dmexe.finagle.consul.ConsulQuery
import com.twitter.finagle.{Http, http}
import com.twitter.util.Await
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FlatSpec}

class AgentServiceSpec extends FlatSpec with BeforeAndAfterEach with BeforeAndAfterAll {
  val httpClient = Http.newService("localhost:8500")
  val service    = new AgentService(httpClient)
  val ia         = new InetSocketAddress("localhost", 12345)
  val q          = ConsulQuery.decodeString("/AgentServiceSpec?ttl=2&tag=tagName").get

  var serviceId  = Option.empty[String]

  override def afterEach: Unit = {
    serviceId foreach service.deregisterService
  }

  override def afterAll: Unit = {
    httpClient.close()
  }

  "AgentService" should "register/deregister service" in {
    // register service
    val regRep = Await.result(service.registerService(ia, q))
    serviceId  = Some(regRep.serviceId)

    assert(regRep.serviceId == "finagle:AgentServiceSpec:127.0.0.1:12345")
    assert(regRep.checkId   == "service:finagle:AgentServiceSpec:127.0.0.1:12345")

    // get health services, it must be empty because service is unhealthy
    var getRep = Await.result(service.getHealthServices(q))
    assert(getRep.isEmpty)

    // service must exists in failed list
    val failRep = Await.result(service.getUnhealthyChecks(q))
    assert(failRep.length == 2)
    assert(failRep.head.serviceId == "finagle:AgentServiceSpec:127.0.0.1:12345")
    assert(failRep.head.status    == "critical")

    // pass health check for service
    Await.result(service.passHealthCheck(regRep.checkId))

    // service must be healthy
    getRep = Await.result(service.getHealthServices(q))
    assert(getRep.length == 1)
    assert(getRep.head.service.id.get  == "finagle:AgentServiceSpec:127.0.0.1:12345")
    assert(getRep.head.service.address == "127.0.0.1")
    assert(getRep.head.service.port == 12345)

    // deregister service
    Await.result(service.deregisterService(regRep.serviceId))
    serviceId = None
  }

  "AgentService" should "mark service as unhealthy" in {
    // register service
    val regRep = Await.result(service.registerService(ia, q))
    serviceId  = Some(regRep.serviceId)

    // pass health check for service
    Await.result(service.passHealthCheck(regRep.checkId))

    // service must be healthy
    var getRep = Await.result(service.getHealthServices(q))
    assert(getRep.length == 1)

    // sleep ttl + 1s
    Thread.sleep(q.ttl.inMillis + 1000)

    // get health services, it must be empty because service is unhealthy
    getRep = Await.result(service.getHealthServices(q))
    assert(getRep.isEmpty)

    // service must exists in failed list
    val failRep = Await.result(service.getUnhealthyChecks(q))
    assert(failRep.length == 2)
    assert(failRep.head.serviceId == "finagle:AgentServiceSpec:127.0.0.1:12345")
    assert(failRep.head.status    == "critical")

    // deregister service
    Await.result(service.deregisterService(regRep.serviceId))
    serviceId = None
  }
}
