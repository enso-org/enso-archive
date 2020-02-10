package org.enso.jsonrpcserver
import io.circe.{Decoder, Encoder, Json}

abstract class MethodTag(val name: String)

case class Serializer[M](decoder: Decoder[M], encoder: Encoder[M]) {
  def decode(payload: Json): Option[M] =
    decoder.decodeJson(payload).toOption
}

case class RequestSerializer[Req, Res](
  requestSerializer: Serializer[Req],
  responseSerializer: Serializer[Res]
) {

  def decodeRequest(payload: Json): Option[Req] =
    requestSerializer.decode(payload)
}

case class Request[+M](tag: MethodTag, id: String, params: M)
case class Notification[+M](tag: MethodTag, params: M)
case class ResponseData[+M](tag: MethodTag, id: Option[String], data: M)

abstract class Error(val code: Int, val message: String)
case object ParseError     extends Error(-32700, "Parse error")
case object InvalidRequest extends Error(-32600, "Invalid Request")
case object MethodNotFound extends Error(-32601, "Method not found")
case object InvalidParams  extends Error(-32602, "Invalid params")

case class ResponseError(id: Option[String], error: Error)

case class Protocol(
  methods: Set[MethodTag],
  requestSerializers: Map[MethodTag, RequestSerializer[_, _]],
  notificationSerializers: Map[MethodTag, Serializer[_]]
) {
  val methodsMap: Map[String, MethodTag] =
    methods.map(tag => (tag.name, tag)).toMap

  def resolveMethod(name: String): Option[MethodTag] = methodsMap.get(name)

  def getRequestSerializer(name: MethodTag): Option[RequestSerializer[_, _]] =
    requestSerializers.get(name)
}
