package com.github.dmexe.finagle.consul.client

import com.twitter.util.Future
import com.twitter.finagle.http

trait HttpResponses {

  import HttpErrors._

  def okResponse(expected: Int, key: String)(resp: http.Response): Future[http.Response] = {
    //println("key: " + key + " code: " + resp.statusCode + " body: " + resp.contentString)
    if (resp.statusCode == expected) {
      Future.value(resp)
    } else if (resp.statusCode == 404) {
      Future.exception(KeyNotFoundError(key))
    } else {
      Future.exception(BadResponse(expected, resp.statusCode, resp.contentString))
    }
  }
}

