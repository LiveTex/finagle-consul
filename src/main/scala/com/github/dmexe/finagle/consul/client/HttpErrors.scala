package com.github.dmexe.finagle.consul.client

object HttpErrors {
  final case class KeyNotFoundError(key: String)
    extends RuntimeException(s"Cannot found key($key)")

  final case class BadResponse(expected: Int, got: Int, body: String)
    extends RuntimeException(s"Expect response code $expected, got $got body($body)")
}
