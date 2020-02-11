package org.enso.jsonrpcserver
import io.circe.{Decoder, Encoder, Json}

abstract class MethodTag(val name: String)

case class Request[+M](tag: MethodTag, id: String, params: M)
case class Notification[+M](tag: MethodTag, params: M)
case class ResponseResult[+M](id: Option[String], data: M)

abstract class Error(val code: Int, val message: String)
case object ParseError     extends Error(-32700, "Parse error")
case object InvalidRequest extends Error(-32600, "Invalid Request")
case object MethodNotFound extends Error(-32601, "Method not found")
case object InvalidParams  extends Error(-32602, "Invalid params")

case class ResponseError(id: Option[String], error: Error)

case class Protocol[P](
  methods: Set[MethodTag],
  requestSerializers: Map[MethodTag, Decoder[P]],
  responseSerializers: Map[MethodTag, Decoder[P]],
  notificationSerializers: Map[MethodTag, Decoder[P]],
  allStuffEncoder: Encoder[P]
) {
  val methodsMap: Map[String, MethodTag] =
    methods.map(tag => (tag.name, tag)).toMap

  def resolveMethod(name: String): Option[MethodTag] = methodsMap.get(name)

  def getRequestSerializer(name: MethodTag): Option[Decoder[P]] =
    requestSerializers.get(name)
}
