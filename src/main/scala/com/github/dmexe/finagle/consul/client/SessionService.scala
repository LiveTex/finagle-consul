package com.github.dmexe.finagle.consul.client

import com.fasterxml.jackson.annotation.JsonProperty
import com.github.dmexe.finagle.consul.common.Json
import com.twitter.finagle.http
import com.twitter.finagle.Service
import com.twitter.util.Future

class SessionService(val client: Service[http.Request, http.Response]) extends HttpRequests with HttpResponses {
  import SessionService._
  import HttpErrors.KeyNotFoundError

  def create(name: String, lockDelay: Int, ttl: Int): Future[String] = {
    val req  = ConsulCreateRequest(lockDelay = s"${lockDelay}s", name = name, behavior = "delete", ttl = s"${ttl}s")
    val key  = "/v1/session/create"
    val body = Json.encode(req)
    httpPut(key, body) flatMap okResponse(200, key) flatMap decodeCreateResponse map(_.id)
  }

  def destroy(session: String): Future[Unit] = {
    val key = s"/v1/session/destroy/$session"
    httpPut(key) flatMap okResponse(200, key) map (_ => ())
  }

  def renew(session: String): Future[Option[ConsulGetResponse]] = {
    val key = s"/v1/session/renew/$session"
    val res = httpPut(key) flatMap okResponse(200, key) flatMap decodeGetResponse
    res rescue {
      case e: KeyNotFoundError => Future.value(None)
    }
  }

  def get(session: String): Future[Option[ConsulGetResponse]] = {
    val key = s"/v1/session/info/$session"
    val res = httpGet(key) flatMap okResponse(200, key) flatMap decodeGetResponse
    res rescue {
      case e: KeyNotFoundError => Future.value(None)
    }
  }

  private def decodeCreateResponse(res: http.Response): Future[ConsulCreateResponse] = {
    Json.decode[ConsulCreateResponse](res.contentString)
  }

  private def decodeGetResponse(res: http.Response): Future[Option[ConsulGetResponse]] = {
    val list = Json.decode[Seq[ConsulGetResponse]](res.contentString)
    list map (_.headOption)
  }
}

object SessionService {
  final case class ConsulCreateRequest(
    @JsonProperty("LockDelay")
    lockDelay:   String,
    @JsonProperty("Name")
    name:        String,
    @JsonProperty("Behavior")
    behavior:    String,
    @JsonProperty("TTL")
    ttl:         String
  )

  final case class ConsulCreateResponse(
    @JsonProperty("ID")
    id: String
  )

  final case class ConsulGetResponse(
    @JsonProperty("LockDelay")
    lockDelay:   BigInt,
    @JsonProperty("Checks")
    checks:      Set[String],
    @JsonProperty("None")
    node:        String,
    @JsonProperty("ID")
    id:          String,
    @JsonProperty("CreateIndex")
    createIndex: BigInt,
    @JsonProperty("Behavior")
    behavior:    String,
    @JsonProperty("TTL")
    ttl:         String
  )
}
