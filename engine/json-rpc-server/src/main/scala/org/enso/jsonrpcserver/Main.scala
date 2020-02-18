package org.enso.jsonrpcserver

import org.enso.jsonrpcserver.Foo.{MyMethod, MyMethod2, MyMethod2Params, MyMethodParams, Request}

import scala.annotation.unused

object Foo {
  trait RequestParams

  case class RequestMatcher[+M <: Method, +P <: RequestParams](instance: Method) {
    def unapply(req: Request[Method, RequestParams]): Option[P] = req match {
      case r: Request[M, P] if r.method == instance => Some(r.params)
      case _                => None
    }
  }

  trait Method {
    type Out <: RequestParams
    type M <: Method
    val name: String
    lazy val request: RequestMatcher[M, Out] = RequestMatcher[M, Out](this)
  }

  object Method {
    type Aux[Meth <: Method, R <: RequestParams] = Method { type Out = R; type M = Meth }
    implicit val instance1: Aux[MyMethod.type, MyMethodParams] = MyMethod
    implicit val instance2: Aux[MyMethod2.type, MyMethod2Params] = MyMethod2
  }

  case class MyMethodParams(thing: Int) extends RequestParams

  case object MyMethod extends Method {
    val name = "Foo"
    type Out = MyMethodParams
    type M = this.type
  }

  case class MyMethod2Params(stuff: String) extends RequestParams

  case object MyMethod2 extends Method {
    val name = "Dupa"
    type Out = MyMethod2Params
    type M = this.type
  }

  case class Request[+M <: Method, +Req <: RequestParams](
    method: M,
    id: Int,
    params: Req
  )(implicit @unused ev: Method.Aux[M, Req])

  def checkRequest(
    req: Request[Method, RequestParams]
  ): Unit = {
    req match {
      case MyMethod.request(p) => println(s"thing! ${p.thing}")
      case MyMethod2.request(p) => println(s"stuff! ${p.stuff}")
      case _ =>
        println("unknown request")
    }
  }
}

object Main {
  def main(args: Array[String]): Unit = {

    val req1 = Request(MyMethod, 0, MyMethodParams(87654))
    val req2 = Request(MyMethod2, 1, MyMethod2Params("jhgfds"))

    Foo.checkRequest(req1)
    Foo.checkRequest(req2)
  }
}
