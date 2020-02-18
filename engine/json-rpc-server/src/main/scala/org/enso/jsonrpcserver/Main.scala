package org.enso.jsonrpcserver

import org.enso.jsonrpcserver.Foo.{MyMethod, MyMethod2, MyMethod2Params, MyMethodParams, Request}

import scala.reflect.ClassTag

object Foo {
  trait RequestParams

//  trait GetRequestParams[+T <: Method] {
//    type Out <: RequestParams
//    val instance: T
//    lazy val requestMatcher = RequestMatcher[T, Out](instance)
//  }
//  object GetRequestParams {
//    type Aux[T <: Method, X] = GetRequestParams[T] { type Out = X }
//  }

  case class RequestMatcher[+M, +P <: RequestParams](instance: Any) {
    def unapply(req: Request[Any, RequestParams]): Option[P] = req match {
      case r: Request[M, P] if r.method == instance => Some(r.params)
      case _                => None
    }
  }

  trait Method[+T] {
    type Out <: RequestParams
    val name: String
    lazy val request = RequestMatcher[T, Out](this)
  }

  object Method {
    type Aux[T, X] = Method[T] { type Out = X }
  }

  case class MyMethodParams(thing: Int) extends RequestParams

  case object MyMethod extends Method[this.type] {
    val name = "Foo"
    type Out = MyMethodParams
    lazy implicit val wtf = this
  }

  case class MyMethod2Params(stuff: String) extends RequestParams

  case object MyMethod2 extends Method[this.type] {
    val name = "Dupa"
    type Out = MyMethod2Params
    lazy implicit val wtf = this
  }

  case class Request[+M <: Method[Any], +RequestParams](
    method: M,
    id: Int,
    params: RequestParams
  )(implicit ev: Method.Aux[M, RequestParams])

//  def parse(foo: Int): Request[Method, RequestParams] = {
//    if (foo == 0) {
//      Request(MyMethod, 0, MyMethodRequestParams(1))
//    } else {
//      ???
//    }
//  }

//  def mkRequest(
//    method: Method,
//    params: RequestParams
//  ): Request[Method, RequestParams] = {
//    Request(method, 0, params)(???)
//  }

  def checkRequest(
    req: Request[Method[Any], RequestParams]
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
