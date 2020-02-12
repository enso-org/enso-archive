package org.enso.jsonrpcserver
import io.circe.{Decoder, Encoder, Json}

abstract class Method(val name: String)

trait DataOf[+M]

trait ParamsOf[+M] extends DataOf[M]
trait ResultOf[+M] extends DataOf[M]

case class Request[+M <: Method](
  tag: M,
  id: String,
  params: ParamsOf[M]
)
case class Notification[+M <: Method](tag: M, params: ParamsOf[M])
case class ResponseResult[+M <: Method](
  id: Option[String],
  data: ResultOf[M]
)

case class UnknownResult(result: Json) extends ResultOf[Method]

abstract class Error(val code: Int, val message: String)
case object ParseError     extends Error(-32700, "Parse error")
case object InvalidRequest extends Error(-32600, "Invalid Request")
case object MethodNotFound extends Error(-32601, "Method not found")
case object InvalidParams  extends Error(-32602, "Invalid params")

case class ResponseError(id: Option[String], error: Error)

case class Protocol(
  methods: Set[Method],
  paramsDecoders: Map[Method, Decoder[ParamsOf[Method]]],
  responseDecoders: Map[Method, Decoder[ResultOf[Method]]],
  allStuffEncoder: Encoder[DataOf[Method]]
) {
  val methodsMap: Map[String, Method] =
    methods.map(tag => (tag.name, tag)).toMap

  def resolveMethod(name: String): Option[Method] = methodsMap.get(name)

  def getParamsDecoder(
    name: Method
  ): Option[Decoder[ParamsOf[Method]]] =
    paramsDecoders.get(name)

  def getResultDecoder(method: Method): Option[Decoder[ResultOf[Method]]] =
    responseDecoders.get(method)
}
