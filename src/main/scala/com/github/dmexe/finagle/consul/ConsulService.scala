package com.github.dmexe.finagle.consul

import java.util.logging.Logger

import com.github.dmexe.finagle.consul.client.KeyService
import com.github.dmexe.finagle.consul.common.Json
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Service => HttpxService}
import com.twitter.util.{Await, Future}

import scala.collection.mutable

class ConsulService(httpClient: HttpxService[Request, Response]) {

  import ConsulService._

  private val log    = Logger.getLogger(getClass.getName)
  private val client = KeyService(httpClient)

  def list(name: String): Seq[Service] = {
    val key = lockName(name)
    val res = client.getAll(key) flatMap decodeServices
    Await.result(res)
  }

  private[consul] def create(service: Service): Unit = {
    val key  = lockName(service.ID, service.Service)
    val body = encodeService(service)
    val res  = client.acquire(key, service.ID, body)
    Await.result(res)
    log.info(s"Consul service registered name=${service.Service} session=${service.ID} addr=${service.Address}:${service.Port}")
  }

  private[consul] def destroy(session: String, name: String): Unit = {
    val reply = client.delete(lockName(session, name))
    Await.result(reply)
    log.info(s"Consul service deregistered name=$name session=$session")
  }

  private def encodeService(service: Service): String = {
    Json.encode[Service](service)
  }

  private def decodeServices(body: Seq[KeyService.Key]): Future[Seq[Service]] = {
    val decoded =
      body.foldLeft(Seq.empty[Future[Service]]) { (memo, s) =>
        s.value match {
          case Some(value) => memo ++ Seq(Json.decode[Service](value))
          case None        => memo
        }
      }
    Future.collect(decoded)
  }

  private def lockName(name: String): String = {
    s"finagle/services/$name"
  }

  private def lockName(session: String, name: String): String = {
    lockName(name) + s"/$session"
  }
}

object ConsulService {
  case class Service(ID: String, Service: String, Address: String, Port: Int, Tags: Set[String], dc: Option[String] = None)

  private val services: mutable.Map[String, ConsulService] = mutable.Map()

  def get(hosts: String): ConsulService = {
    synchronized {
      val service = services.getOrElseUpdate(hosts, {
        val newClient = client.HttpClientFactory.getClient(hosts)
        new ConsulService(newClient)
      })
      service
    }
  }
}
