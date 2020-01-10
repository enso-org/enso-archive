package org.enso.gateway.protocol.response

import io.circe.Encoder
import io.circe.generic.extras.semiauto.deriveUnwrappedEncoder
import io.circe.generic.semiauto.deriveEncoder
import org.enso.gateway.protocol.response.result.{
  ServerCapabilities,
  ServerInfo
}
import io.circe.syntax._

/** LSP Spec:
  * https://microsoft.github.io/language-server-protocol/specifications/specification-3-15/#responseMessage
  *
  * [[org.enso.gateway.protocol.Response]] result
  */
sealed trait Result

object Result {
  implicit val resultEncoder: Encoder[Result] = Encoder.instance {
    case string: String                     => string.asJson
    case number: Number                     => number.asJson
    case boolean: Boolean                   => boolean.asJson
    case initializeResult: InitializeResult => initializeResult.asJson
  }

  case class String(value: Predef.String) extends Result

  object String {
    implicit val resultStringEncoder: Encoder[String] = deriveUnwrappedEncoder
  }

  case class Number(value: Int) extends Result

  object Number {
    implicit val resultNumberEncoder: Encoder[Number] = deriveUnwrappedEncoder
  }

  case class Boolean(value: scala.Boolean) extends Result

  object Boolean {
    implicit val resultBooleanEncoder: Encoder[Boolean] =
      deriveUnwrappedEncoder
  }

  /**
    * [[org.enso.gateway.protocol.Requests.Initialize]] result
    */
  case class InitializeResult(
    capabilities: ServerCapabilities,
    serverInfo: Option[ServerInfo] = None
  ) extends Result

  object InitializeResult {
    implicit val initializeResultEncoder: Encoder[InitializeResult] =
      deriveEncoder
  }
}
