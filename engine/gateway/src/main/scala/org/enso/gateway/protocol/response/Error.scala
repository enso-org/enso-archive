package org.enso.gateway.protocol.response

import io.circe.generic.semiauto.deriveEncoder
import io.circe.Encoder
import org.enso.gateway.protocol.response.error.Data
import org.enso.gateway.protocol.response.error.Data.InitializeData
import io.circe.syntax._

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
  implicit val errorEncoder: Encoder[Error] = Encoder.instance {
    case initializeError: InitializeError => initializeError.asJson
  }

  /**
    * [[org.enso.gateway.protocol.Requests.Initialize]] error
    */
  case class InitializeError(
    code: Int, // ErrorCodes.unknownProtocolVersion
    message: String,
    data: Option[InitializeData] = None
  ) extends Error

  object InitializeError {
    implicit val initializeErrorEncoder: Encoder[InitializeError] =
      deriveEncoder
  }
}
