package com.github.dmexe.finagle.consul.client

import com.github.dmexe.finagle.consul.common.Json
import com.twitter.finagle.http
import com.twitter.finagle.{Service => HttpService}
import com.twitter.util.Future

class SessionService(val client: HttpService[http.Request, http.Response]) extends HttpRequests with HttpResponses {
  import SessionService._
  import HttpErrors.KeyNotFoundError

  def create(createRequest: CreateRequest): Future[CreateResponse] = {
    val key  = "/v1/session/create"
    val body = Json.encode(createRequest)
    httpPut(key, body) flatMap okResponse(200, key) flatMap decodeCreateResponse
  }

  def destroy(session: String): Future[Unit] = {
    val key = s"/v1/session/destroy/$session"
    httpPut(key) flatMap okResponse(200, key) map (_ => ())
  }

  def renew(session: String): Future[Option[SessionResponse]] = {
    val key = s"/v1/session/renew/$session"
    val res = httpPut(key) flatMap okResponse(200, key) flatMap decodeSessionResponse
    res rescue {
      case e: KeyNotFoundError => Future.value(None)
    }
  }

  def info(session: String): Future[Option[SessionResponse]] = {
    val key = s"/v1/session/info/$session"
    val res = httpGet(key) flatMap okResponse(200, key) flatMap decodeSessionResponse
    res rescue {
      case e: KeyNotFoundError => Future.value(None)
    }
  }

  private def decodeCreateResponse(res: http.Response): Future[CreateResponse] = {
    Json.decode[CreateResponse](res.contentString)
  }

  private def decodeSessionResponse(res: http.Response): Future[Option[SessionResponse]] = {
    val list = Json.decode[Seq[SessionResponse]](res.contentString)
    list map (_.headOption)
  }
}

object SessionService {
  final case class CreateRequest(LockDelay: String, Name: String, Behavior: String, TTL: String)
  final case class CreateResponse(ID: String)
  final case class SessionResponse(LockDelay: BigInt, Checks: Set[String], Node: String, ID: String, CreateIndex: BigInt, Behavior: String, TTL: String)

  def apply(httpClient: HttpService[http.Request, http.Response]) = new SessionService(httpClient)
}
