package org.enso.jsonrpcserver
import io.circe.{Decoder, Encoder, Json}

abstract class MethodTag[+P](val name: String)

case class Request[+M](tag: MethodTag[M], id: String, params: M)
case class Notification[+M](tag: MethodTag[M], params: M)
case class ResponseResult[+M](id: Option[String], data: M)

abstract class Error(val code: Int, val message: String)
case object ParseError     extends Error(-32700, "Parse error")
case object InvalidRequest extends Error(-32600, "Invalid Request")
case object MethodNotFound extends Error(-32601, "Method not found")
case object InvalidParams  extends Error(-32602, "Invalid params")

case class ResponseError(id: Option[String], error: Error)

case class Protocol[P](
  methods: Set[MethodTag[P]],
  requestSerializers: Map[MethodTag[P], Decoder[P]],
  responseSerializers: Map[MethodTag[P], Decoder[P]],
  notificationSerializers: Map[MethodTag[P], Decoder[P]],
  allStuffEncoder: Encoder[P]
) {
  val methodsMap: Map[String, MethodTag[P]] =
    methods.map(tag => (tag.name, tag)).toMap

  def resolveMethod(name: String): Option[MethodTag[P]] = methodsMap.get(name)

  def getRequestSerializer(name: MethodTag[P]): Option[Decoder[P]] =
    requestSerializers.get(name)
}
