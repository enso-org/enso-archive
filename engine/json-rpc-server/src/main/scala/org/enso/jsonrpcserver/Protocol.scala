package org.enso.jsonrpcserver
import io.circe.Decoder.Result
import io.circe.{Decoder, Encoder, HCursor, Json}

sealed trait Id
case class StringId(id: String) extends Id
case class NumberId(id: Int) extends Id

object Id {
  import io.circe.syntax._
  import cats.syntax.functor._
  implicit val encoder: Encoder[Id] = {
    case StringId(id) => id.asJson
    case NumberId(id) => id.asJson
  }
  implicit val decoder: Decoder[Id] = Decoder[String]
    .map(StringId)
    .widen[Id]
    .or(Decoder[Int].map(NumberId).widen[Id])
}

abstract class Method(val name: String)

trait PayloadOf[+M]

trait ParamsOf[+M] extends PayloadOf[M]
trait ResultOf[+M] extends PayloadOf[M]

case class Request[+M <: Method](
  tag: M,
  id: Id,
  params: ParamsOf[M]
)

case class Notification[+M <: Method](tag: M, params: ParamsOf[M])

case class ResponseResult[+M <: Method](
  id: Option[Id],
  data: ResultOf[M]
)

case class UnknownResult(result: Json) extends ResultOf[Method]

abstract class Error(val code: Int, val message: String)
case object ParseError     extends Error(-32700, "Parse error")
case object InvalidRequest extends Error(-32600, "Invalid Request")
case object MethodNotFound extends Error(-32601, "Method not found")
case object InvalidParams  extends Error(-32602, "Invalid params")

case class UnknownError(override val code: Int, override val message: String)
    extends Error(code, message)

case class ResponseError(id: Option[Id], error: Error)

case class Protocol(
  methods: Set[Method],
  paramsDecoders: Map[Method, Decoder[ParamsOf[Method]]],
  responseDecoders: Map[Method, Decoder[ResultOf[Method]]],
  customErrors: Map[Int, Error],
  payloadsEncoder: Encoder[PayloadOf[Method]]
) {
  val builtinErrors: Map[Int, Error] = List(
    ParseError,
    InvalidRequest,
    MethodNotFound,
    InvalidParams
  ).map(err => err.code -> err).toMap

  val methodsMap: Map[String, Method] =
    methods.map(tag => (tag.name, tag)).toMap

  def resolveMethod(name: String): Option[Method] = methodsMap.get(name)

  def getParamsDecoder(
    name: Method
  ): Option[Decoder[ParamsOf[Method]]] =
    paramsDecoders.get(name)

  def getResultDecoder(method: Method): Option[Decoder[ResultOf[Method]]] =
    responseDecoders.get(method)

  def resolveError(code: Int): Option[Error] =
    builtinErrors.get(code).orElse(customErrors.get(code))
}
