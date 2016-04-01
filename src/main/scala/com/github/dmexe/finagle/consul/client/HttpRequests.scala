package com.github.dmexe.finagle.consul.client

import com.twitter.finagle.http
import com.twitter.finagle.Service
import com.twitter.util.Future

trait HttpRequests {

  val client: Service[http.Request, http.Response]

  def httpGet(path: String): Future[http.Response] = {
    val req = http.Request(http.Method.Get, path)
    req.setContentTypeJson()
    req.headerMap.add("Host", "localhost")
    client(req)
  }

  def httpPut(path: String, body: String): Future[http.Response] = {
    val req  = http.Request(http.Method.Put, path)
    req.setContentTypeJson()
    req.headerMap.add("Host", "localhost")
    req.write(body)
    client(req)
  }

  def httpPut(path: String): Future[http.Response] = httpPut(path, "")

  def httpDelete(path: String): Future[http.Response] = {
    val req  = http.Request(http.Method.Delete, path)
    req.setContentTypeJson()
    req.headerMap.add("Host", "localhost")
    client(req)
  }
}

