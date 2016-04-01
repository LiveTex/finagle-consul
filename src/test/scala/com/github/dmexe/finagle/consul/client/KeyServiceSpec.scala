package com.github.dmexe.finagle.consul.client

import com.twitter.finagle.{Http, http}
import com.twitter.util.Await
import org.scalatest.WordSpec
import com.github.dmexe.finagle.consul.common.Json

object KeyServiceSpec {
  final case class Value(name: String)
  final case class Session(ID: String)
}

class KeyServiceSpec extends WordSpec {

  import KeyServiceSpec._

  val httpClient = Http.newService("localhost:8500")
  val service    = KeyService(httpClient)

  "simple create/get/destroy" in {
    val path  = "test/key0"
    val value = Value("test")
    val body  = Json.encode(value)

    val Some(createReply) = Await.result(service.put(path, body))
    assert(createReply.session.isEmpty)
    assert(createReply.key == path)
    assert(createReply.value.get == "{\"name\":\"test\"}")

    val Some(getReply) = Await.result(service.get(path))
    assert(getReply.session.isEmpty)
    assert(getReply.key == path)
    assert(getReply.value.get == "{\"name\":\"test\"}")

    Await.result(service.delete(path))

    val allReply = Await.result(service.getAll(path))
    assert(allReply.isEmpty)
  }

  "find recursive" in {
    val path0 = "test/key/0"
    val path1 = "test/key/1"
    val path2 = "test/key/1/2"
    val value0 = Value("test0")
    val value1 = Value("test1")
    val value2 = Value("test2")
    val body0  = Json.encode(value0)
    val body1  = Json.encode(value1)
    val body2  = Json.encode(value2)

    val Some(_) = Await.result(service.put(path0, body0))
    val Some(_) = Await.result(service.put(path1, body1))
    val Some(_) = Await.result(service.put(path2, body2))

    val allReply = Await.result(service.getAll("test/key"))
    assert(allReply.size == 3)
    assert(allReply.map(_.key)   == Seq("test/key/0", "test/key/1", "test/key/1/2"))
    assert(allReply.map(_.value.get) == Seq("{\"name\":\"test0\"}", "{\"name\":\"test1\"}", "{\"name\":\"test2\"}"))

    Await.result(service.delete(path0))
    Await.result(service.delete(path1))
    Await.result(service.delete(path2))
  }

  "acquire/release" in {
    val lock        = "test/lock0"
    val value       = Value("test")
    val body        = Json.encode(value)
    val sessionBody = s"""{ "LockDelay": "10s", "Name": "test", "Behavior": "delete", "TTL": "10s" }"""
    val createSession = http.Request(http.Method.Put, "/v1/session/create")
    createSession.setContentString(sessionBody)
    createSession.headerMap.add("Host", "localhost")

    val sessionReply0 = Await.result(httpClient(createSession))
    val sessionReply1 = Await.result(httpClient(createSession))

    val session0 = Await.result(Json.decode[Session](sessionReply0.contentString))
    val session1 = Await.result(Json.decode[Session](sessionReply1.contentString))

    assert(Await.result(service.acquire(lock, session0.ID, body)))

    assert(!Await.result(service.acquire(lock, session1.ID, body)))

    assert(Await.result(service.get(lock)).head.session.head == session0.ID)

    assert(Await.result(service.release(lock, session0.ID)))

    assert(Await.result(service.get(lock)).head.session.isEmpty)
    assert(Await.result(service.get(lock)).head.value.isEmpty)

    service.delete(lock)
  }
}
