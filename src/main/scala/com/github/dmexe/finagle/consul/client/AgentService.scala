package com.github.dmexe.finagle.consul.client

import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

import com.fasterxml.jackson.annotation.JsonProperty
import com.github.dmexe.finagle.consul.ConsulQuery
import com.github.dmexe.finagle.consul.common.Json
import com.twitter.finagle.{http, Service}
import com.twitter.util.{Duration, Future}

class AgentService(val client: Service[http.Request, http.Response]) extends HttpRequests with HttpResponses {

  import AgentService._

  def registerService(ia: InetSocketAddress, q: ConsulQuery): Future[ConsulRegisterResponse] = {
    val address    = ia.getAddress.getHostAddress
    val port       = ia.getPort
    val serviceId  = s"finagle:${q.name}:$address:$port"
    val checkId    = s"service:$serviceId"
    val key        = s"/v1/agent/service/register"
    val check      = TtlCheckRequest(ttl = formatTtl(q.ttl))

    val serviceDef = ConsulServiceRequest(
      id      = Some(serviceId),
      name    = q.name,
      tags    = q.tags,
      address = Some(address),
      port    = Some(port),
      check   = Some(check)
    )

    val body = Json.encode(serviceDef)
    val resp = ConsulRegisterResponse(serviceId, checkId)
    httpPut(key, body) flatMap okResponse(200, key) map (_ => resp)
  }

  def deregisterService(serviceId: String): Future[Unit] = {
    val key = s"/v1/agent/service/deregister/$serviceId"
    httpPut(key, "") flatMap okResponse(200, key) map (_ => ())
  }

  def passHealthCheck(checkId: String): Future[Unit] = {
    val key = s"/v1/agent/check/pass/$checkId"
    httpGet(key) flatMap okResponse(200, key) map (_ => ())
  }

  def getHealthServices(q: ConsulQuery): Future[Seq[ConsulHealthResponse]] = {
    val params = Seq(datacenterParam(q), tagParams(q)).flatten :+ ("passing", "true")
    val key    = s"/v1/health/service/${q.name}"
    httpGet(key, params) flatMap okResponse(200, key) flatMap decodeHealthServices
  }

  def getUnhealthyChecks(q: ConsulQuery): Future[Seq[ConsulHealthStateResponse]] = {
    val params = Seq(datacenterParam(q)).flatten
    val key    = "/v1/health/state/critical"
    httpGet(key, params) flatMap okResponse(200, key) flatMap decodeHealthStates
  }

  private def formatTtl(d: Duration): String = s"${d.inUnit(TimeUnit.SECONDS)}s"

  private def datacenterParam(q: ConsulQuery): List[(String, String)] = {
    q.dc
      .map { dc => List("dc" -> dc) }
      .getOrElse(List.empty)
  }

  private def tagParams(q: ConsulQuery): List[(String, String)] = {
    q.tags.toList.map { "tag" -> _ }
  }

  private def decodeHealthServices(resp: http.Response): Future[Seq[ConsulHealthResponse]] = {
    Json.decode[Seq[ConsulHealthResponse]](resp.contentString)
  }

  private def decodeHealthStates(resp: http.Response): Future[Seq[ConsulHealthStateResponse]] = {
    Json.decode[Seq[ConsulHealthStateResponse]](resp.contentString)
  }
}

object AgentService {

  sealed trait ConsulCheckRequest

  final case class TtlCheckRequest(
    @JsonProperty("TTL")
    ttl: String
  ) extends ConsulCheckRequest

  final case class ConsulServiceRequest(
    @JsonProperty("ID")
    id:      Option[String],
    @JsonProperty("Name")
    name:    String,
    @JsonProperty("Tags")
    tags:    Set[String],
    @JsonProperty("Address")
    address: Option[String],
    @JsonProperty("Port")
    port:    Option[Int],
    @JsonProperty("Check")
    check:   Option[ConsulCheckRequest]
  )

  final case class ConsulRegisterResponse (
    serviceId: String,
    checkId:   String
  )

  final case class ConsulHealthResponse(
    @JsonProperty("Service")
    service: ConsulHealthServiceResponse
  )

  final case class ConsulHealthServiceResponse(
    @JsonProperty("ID")
    id:      Option[String],
    @JsonProperty("Service")
    service: String,
    @JsonProperty("Address")
    address: String,
    @JsonProperty("Tags")
    tags:    Seq[String],
    @JsonProperty("Port")
    port:    Int
  )

  final case class ConsulHealthStateResponse(
    @JsonProperty("Node")
    node:        String,
    @JsonProperty("CheckID")
    checkId:     String,
    @JsonProperty("Name")
    name:        String,
    @JsonProperty("Status")
    status:      String,
    @JsonProperty("Notes")
    notes:       String,
    @JsonProperty("Output")
    output:      String,
    @JsonProperty("ServiceID")
    serviceId:   String,
    @JsonProperty("ServiceName")
    serviceName: String
  )
}
