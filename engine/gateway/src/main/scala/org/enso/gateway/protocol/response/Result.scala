package org.enso.gateway.protocol.response

import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{
  deriveUnwrappedDecoder,
  deriveUnwrappedEncoder
}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.enso.gateway.protocol.response.result.{
  ServerCapabilities,
  ServerInfo
}
import cats.syntax.functor._
import io.circe.shapes._
import org.enso.gateway.Protocol.ShapesDerivation._

/** LSP Spec:
  * https://microsoft.github.io/language-server-protocol/specifications/specification-3-15/#responseMessage
  *
  * [[org.enso.gateway.protocol.Response]] result
  */
sealed trait Result

object Result {
  implicit val resultDecoder: Decoder[Result] = List[Decoder[Result]](
    Decoder[Number].widen,
    Decoder[Boolean].widen,
    Decoder[String].widen,
    Decoder[InitializeResult].widen
  ).reduceLeft(_ or _)

  case class String(value: Predef.String) extends Result

  object String {
    implicit val resultStringEncoder: Encoder[String] = deriveUnwrappedEncoder
    implicit val resultStringDecoder: Decoder[String] = deriveUnwrappedDecoder
  }

  case class Number(value: Int) extends Result

  object Number {
    implicit val resultNumberEncoder: Encoder[Number] = deriveUnwrappedEncoder
    implicit val resultNumberDecoder: Decoder[Number] = deriveUnwrappedDecoder
  }

  case class Boolean(value: scala.Boolean) extends Result

  object Boolean {
    implicit val resultBooleanEncoder: Encoder[Boolean] =
      deriveUnwrappedEncoder
    implicit val resultBooleanDecoder: Decoder[Boolean] =
      deriveUnwrappedDecoder
  }

  /**
    * [[org.enso.gateway.protocol.initialize]] result
    */
  case class InitializeResult(
    capabilities: ServerCapabilities,
    serverInfo: Option[ServerInfo] = None
  ) extends Result

  object InitializeResult {
    implicit val initializeResultEncoder: Encoder[InitializeResult] =
      deriveEncoder
    implicit val initializeResultDecoder: Decoder[InitializeResult] =
      deriveDecoder
  }

}
