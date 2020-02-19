package org.enso.jsonrpcserver
import io.circe.{Decoder, Encoder, Json}

import scala.reflect.ClassTag

/**
  * Represents valid JSON RPC request ids.
  */
sealed trait Id

object Id {
  import io.circe.syntax._
  import cats.syntax.functor._

  case class String(id: scala.Predef.String) extends Id
  case class Number(id: Int)                 extends Id

  implicit val encoder: Encoder[Id] = {
    case String(id) => id.asJson
    case Number(id) => id.asJson
  }
  implicit val decoder: Decoder[Id] = Decoder[scala.Predef.String]
    .map(String)
    .widen[Id]
    .or(Decoder[Int].map(Number).widen[Id])
}

case object Unused {
  implicit val encoder: Encoder[this.type] = { _ =>
    Json.Null
  }
  implicit val decoder: Decoder[this.type] = { _ =>
    Right(Unused)
  }
}

trait HasParams[M <: Method] {
  type Params
}
object HasParams {
  type Aux[M <: Method, P] = HasParams[M] { type Params = P }
}

trait HasResult[M <: Method] {
  type Result
}
object HasResult {
  type Aux[M <: Method, Res] = HasResult[M] { type Result = Res }
}

/**
  * A superclass for supported API methods.
  * @param name the method name.
  */
abstract class Method(val name: String)

/**
  * The basic JSON RPC request type.
  */
case class Request[+M <: Method, +Params](
  method: M,
  id: Id,
  params: Params
)(implicit ev: HasParams.Aux[M, Params])

/**
  * The basic JSON RPC notification type.
  */
case class Notification[+M <: Method, +Params](method: M, params: Params)(
  implicit ev: HasParams.Aux[M, Params]
)

/**
  * The basic JSON RPC successful response type.
  */
case class ResponseResult[+M <: Method, +Result](
  method: M,
  id: Id,
  data: Result
)(implicit ev: HasResult.Aux[M, Result])

/**
  * The basic JSON RPC error response type.
  */
case class ResponseError(id: Option[Id], error: Error)

/**
  * A basic error type for responses.
  * @param code the error code.
  * @param message the error message.
  */
abstract class Error(val code: Int, val message: String)

/**
  * Builtin error types, defined by JSON RPC.
  */
object Errors {
  case object ParseError     extends Error(-32700, "Parse error")
  case object InvalidRequest extends Error(-32600, "Invalid Request")
  case object MethodNotFound extends Error(-32601, "Method not found")
  case object InvalidParams  extends Error(-32602, "Invalid params")
  case class UnknownError(override val code: Int, override val message: String)
      extends Error(code, message)
}

object Protocol {
  case class InexhaustivePayloadsSerializerError(payload: Any) extends Exception

  def empty =
    Protocol(Set(), Map(), Map(), Map(), { payload =>
      throw InexhaustivePayloadsSerializerError(payload)
    })
}

class ParamsDecoder[+M <: Method, +Params](method: M)(
  implicit ev: HasParams.Aux[M, Params],
  decoder: Decoder[Params]
) {
  def buildRequest(id: Id, params: Json): Option[Request[M, Params]] =
    decoder.decodeJson(params).toOption.map(Request(method, id, _))

  def buildNotification(params: Json): Option[Notification[M, Params]] =
    decoder.decodeJson(params).toOption.map(Notification(method, _))
}

class ResultDecoder[+M <: Method, +Result](method: M)(
  implicit ev: HasResult.Aux[M, Result],
  decoder: Decoder[Result]
) {
  def buildResponse(id: Id, result: Json): Option[ResponseResult[M, Result]] =
    decoder.decodeJson(result).toOption.map(ResponseResult(method, id, _))
}

/**
  * A description containing all the supported methods and ways of serializing
  * their params and results.
  *
  * @param methods all the supported methods.
  * @param paramsDecoders decoders for all supported methods' params.
  * @param resultDecoders decoders for all supported request methods' results.
  * @param customErrors custom datatypes used for error codes.
  * @param payloadsEncoder an encoder for any payload (i.e. params or results)
  *                        used within this protocol.
  */
case class Protocol(
  methods: Set[Method],
  paramsDecoders: Map[Method, ParamsDecoder[Method, Any]],
  resultDecoders: Map[Method, ResultDecoder[Method, Any]],
  customErrors: Map[Int, Error],
  payloadsEncoder: Encoder[Any]
) {

  private val builtinErrors: Map[Int, Error] = List(
    Errors.ParseError,
    Errors.InvalidRequest,
    Errors.MethodNotFound,
    Errors.InvalidParams
  ).map(err => err.code -> err).toMap

  private val methodsMap: Map[String, Method] =
    methods.map(tag => (tag.name, tag)).toMap

  /**
    * Resolves a method by name.
    *
    * @param name the method name.
    * @return an object representing the method, if exists.
    */
  def resolveMethod(name: String): Option[Method] = methodsMap.get(name)

  /**
    * Looks up a params decoder for a given method.
    *
    * @param method the method to lookup decoder for.
    * @return the params decoder, if found.
    */
  def getParamsDecoder(
    method: Method
  ): Option[ParamsDecoder[Method, Any]] =
    paramsDecoders.get(method)

  /**
    * Looks up a result decoder for a given method.
    *
    * @param method the method to lookup decoder for.
    * @return the result decoder, if found.
    */
  def getResultDecoder(method: Method): Option[ResultDecoder[Method, Any]] =
    resultDecoders.get(method)

  /**
    * Looks up a proper error object by error code.
    *
    * @param code the error code to look up.
    * @return the corresponding custom error object, if exists.
    */
  def resolveError(code: Int): Option[Error] =
    builtinErrors.get(code).orElse(customErrors.get(code))

  def registerRequest[
    M <: Method,
    Params: ClassTag,
    Result: ClassTag
  ](method: M)(
    implicit paramsEv: HasParams.Aux[M, Params],
    resultEv: HasResult.Aux[M, Result],
    penc: Encoder[Params],
    pdec: Decoder[Params],
    renc: Encoder[Result],
    rdec: Decoder[Result]
  ): Protocol =
    copy(
      methods = methods + method,
      paramsDecoders =
        paramsDecoders + (method -> new ParamsDecoder(method)),
      resultDecoders =
        resultDecoders + (method -> new ResultDecoder(method)),
      payloadsEncoder = {
        case params: Params => penc.apply(params)
        case result: Result => renc.apply(result)
        case other          => payloadsEncoder(other)
      }
    )

  def registerNotification[
    M <: Method,
    Params: ClassTag
  ](method: M)(
    implicit paramsEv: HasParams.Aux[M, Params],
    paramsEncoder: Encoder[Params],
    paramsDecoder: Decoder[Params]
  ): Protocol =
    copy(
      methods = methods + method,
      paramsDecoders =
        paramsDecoders + (method -> new ParamsDecoder(method)),
      payloadsEncoder = {
        case params: Params => paramsEncoder.apply(params)
        case other          => payloadsEncoder(other)
      }
    )

  def registerError(error: Error): Protocol =
    copy(customErrors = customErrors + (error.code -> error))
}
