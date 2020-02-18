package org.enso.jsonrpcserver

import org.enso.jsonrpcserver.Foo.{MyMethod, MyMethod2, MyMethod2Params, MyMethodParams, Request}

import scala.reflect.ClassTag

object Foo {
  trait RequestParams

  trait GetRequestParams[+T <: Method] {
    type Out <: RequestParams
    val instance: T
    lazy val requestMatcher = RequestMatcher[T, Out](instance)
  }
  object GetRequestParams {
    type Aux[T <: Method, X] = GetRequestParams[T] { type Out = X }
  }

  case class RequestMatcher[+M <: Method, +P <: RequestParams](instance: Method) {
    def unapply(req: Request[Method, RequestParams]): Option[P] = req match {
      case r: Request[M, P] if r.method == instance => Some(r.params)
      case _                => None
    }
  }

  trait Method {
    val name: String
    implicit val requestParams: GetRequestParams[Method]
    lazy val request = requestParams.requestMatcher
  }

  case class MyMethodParams(thing: Int) extends RequestParams

  case object MyMethod extends Method {
    val name = "Foo"
    implicit override val requestParams =
      new GetRequestParams[MyMethod.type] {
        type Out = MyMethodParams
        lazy val instance: MyMethod.type = MyMethod
      }
  }

  case class MyMethod2Params(stuff: String) extends RequestParams

  case object MyMethod2 extends Method {
    val name = "Dupa"
    implicit override val requestParams = new GetRequestParams[MyMethod2.type] {
      type Out = MyMethod2Params
      lazy val instance: MyMethod2.type = MyMethod2
    }
  }

  case class Request[+M <: Method, +RequestParams](
    method: M,
    id: Int,
    params: RequestParams
  )(implicit ev: GetRequestParams.Aux[M, RequestParams])

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
    val req2 = Request(MyMethod2, 1, MyMethod2Params("567"))

    Foo.checkRequest(req1)
    Foo.checkRequest(req2)
  }
}
