package com.github.dmexe.finagle.consul.common

import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.twitter.util.Future

import scala.util.{Failure, Success, Try}

object Json {
  val mapper = new ObjectMapper() with ScalaObjectMapper
  mapper.registerModule(DefaultScalaModule)
  mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  def decode[T](s: String)(implicit m : Manifest[T]): Future[T] = {
    Try { mapper.readValue[T](s) } match {
      case Success(v) => Future.value(v)
      case Failure(e) => Future.exception(e)
    }
  }

  def encode[T](obj: T)(implicit m : Manifest[T]): String = {
    mapper.writeValueAsString(obj)
  }
}
