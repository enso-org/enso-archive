package org.enso.gateway.protocol.response

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import org.enso.gateway.protocol.response.error.Data
import org.enso.gateway.protocol.response.error.Data.InitializeData
import cats.syntax.functor._
import io.circe.shapes._
import org.enso.gateway.Protocol.ShapesDerivation._

/**
  * `ResponseError` in LSP Spec:
  * https://microsoft.github.io/language-server-protocol/specifications/specification-3-15/#responseMessage
  *
  * [[org.enso.gateway.protocol.Response]] error
  */
sealed trait Error {
  def code: Int

  def message: String

  def data: Option[Data]
}

object Error {
  implicit val errorDecoder: Decoder[Error] = List[Decoder[Error]](
    Decoder[InitializeError].widen
  ).reduceLeft(_ or _)

  /**
    * [[org.enso.gateway.protocol.initialize]] error
    */
  case class InitializeError(
    code: Int, // ErrorCodes.unknownProtocolVersion
    message: String,
    data: Option[InitializeData] = None
  ) extends Error

  object InitializeError {
    implicit val initializeErrorEncoder: Encoder[InitializeError] =
      deriveEncoder
    implicit val initializeErrorDecoder: Decoder[InitializeError] =
      deriveDecoder
  }

}
