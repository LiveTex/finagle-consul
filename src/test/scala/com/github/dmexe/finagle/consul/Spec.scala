package com.github.dmexe.finagle.consul

import com.twitter.finagle.Http
import scala.collection.mutable
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

trait Spec extends WordSpec with Matchers with BeforeAndAfterAll {

  val httpClient = Http.client.newService("localhost:8500")

  override def afterAll = {
    //httpClient.close()
    println("!! 2")
  }

  def Deferable[A](context: SpecHelper.DeferTracker => A) = {
    val dt  = new SpecHelper.DeferTracker()
    try {
      context(dt)
    } finally dt.makeCalls
  }
}

object SpecHelper {
  class DeferTracker() {
    class LazyVal[A](val value:() => A)

    private var l = List[LazyVal[Any]]()
    def apply(f: => Any) = { l = new LazyVal(() => f) :: l }
    def makeCalls() = l.foreach { x => x.value() }
  }
}
