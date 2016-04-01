package com.github.dmexe.finagle.consul.common

import com.twitter.util.Future
import scala.util.{Try,Success,Failure}

object Ops {
  implicit class TryToFuture[A](a: Try[A]) {
    def toFuture: Future[A] = {
      a match {
        case Success(a) => Future.value(a)
        case Failure(e) => Future.exception(e)
      }
    }
  }
}
