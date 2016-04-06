package com.github.dmexe.finagle.consul

import com.twitter.finagle.Http
import scala.collection.mutable
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

trait Spec extends WordSpec with Matchers with BeforeAndAfterAll {

  val client  = Http.newService("localhost:8500")

  override def afterAll = {
    client.close()
  }

  def Deferable[A](context: Spec.DeferTracker => A) = {
    val dt  = new Spec.DeferTracker()
    try {
      context(dt)
    } finally dt.makeCalls
  }
}

object Spec {
  class DeferTracker() {
    class LazyVal[A](val value:() => A)

    private var l = List[LazyVal[Any]]()
    def apply(f: => Any) = { l = new LazyVal(() => f) :: l }
    def makeCalls() = l.foreach { x => x.value() }
  }
}
