package com.github.dmexe.finagle.consul.client

import com.twitter.finagle.{Http, http}
import com.twitter.util.Await
import org.scalatest.{BeforeAndAfterAll, WordSpec}
import com.github.dmexe.finagle.consul.common.Json
import com.github.dmexe.finagle.consul.Spec

object KeyServiceSpec {
  final case class Value(name: String)
  final case class Session(ID: String)
}

class KeyServiceSpec extends Spec {

  import KeyServiceSpec._

  val kv = new KeyService(client)

  "KeyService" should {

    "just create/get/destroy" in {
      val path  = "test/key0"
      val value = Value("test")
      val body  = Json.encode(value)

      val Some(createReply) = Await.result(kv.put(path, body))
      assert(createReply.session.isEmpty)
      assert(createReply.key == path)
      assert(createReply.value.get == "{\"name\":\"test\"}")

      val Some(getReply) = Await.result(kv.get(path))
      assert(getReply.session.isEmpty)
      assert(getReply.key == path)
      assert(getReply.value.get == "{\"name\":\"test\"}")

      Await.result(kv.delete(path))

      val allReply = Await.result(kv.getAll(path))
      assert(allReply.isEmpty)
    }

    "find keys recursive" in {
      val path0 = "test/key/0"
      val path1 = "test/key/1"
      val path2 = "test/key/1/2"
      val value0 = Value("test0")
      val value1 = Value("test1")
      val value2 = Value("test2")
      val body0  = Json.encode(value0)
      val body1  = Json.encode(value1)
      val body2  = Json.encode(value2)

      val Some(_) = Await.result(kv.put(path0, body0))
      val Some(_) = Await.result(kv.put(path1, body1))
      val Some(_) = Await.result(kv.put(path2, body2))

      val allReply = Await.result(kv.getAll("test/key"))
      assert(allReply.size == 3)
      assert(allReply.map(_.key)   == Seq("test/key/0", "test/key/1", "test/key/1/2"))
      assert(allReply.map(_.value.get) == Seq("{\"name\":\"test0\"}", "{\"name\":\"test1\"}", "{\"name\":\"test2\"}"))

      Await.result(kv.delete(path0))
      Await.result(kv.delete(path1))
      Await.result(kv.delete(path2))
    }

    "acquire/release lock" in {
      val lock        = "test/lock0"
      val value       = Value("test")
      val body        = Json.encode(value)
      val sessionBody = s"""{ "LockDelay": "10s", "Name": "test", "Behavior": "delete", "TTL": "10s" }"""
      val createSession = http.Request(http.Method.Put, "/v1/session/create")
      createSession.setContentString(sessionBody)
      createSession.headerMap.add("Host", "localhost")

      val sessionReply0 = Await.result(client(createSession))
      val sessionReply1 = Await.result(client(createSession))

      val session0 = Await.result(Json.decode[Session](sessionReply0.contentString))
      val session1 = Await.result(Json.decode[Session](sessionReply1.contentString))

      assert(Await.result(kv.acquire(lock, session0.ID, body)))

      assert(!Await.result(kv.acquire(lock, session1.ID, body)))

      assert(Await.result(kv.get(lock)).head.session.head == session0.ID)

      assert(Await.result(kv.release(lock, session0.ID)))

      assert(Await.result(kv.get(lock)).head.session.isEmpty)
      assert(Await.result(kv.get(lock)).head.value.isEmpty)

      Await.result(kv.delete(lock))
    }
  }

}
