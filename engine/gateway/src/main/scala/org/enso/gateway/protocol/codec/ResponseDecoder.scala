package org.enso.gateway.protocol.codec

import io.circe.{Decoder, DecodingFailure}
import org.enso.gateway.protocol.{Id, Response}
import org.enso.gateway.protocol.response.{ResponseError, Result}
import org.enso.gateway.protocol.response.error.ErrorMessage

object ResponseDecoder {
  val instance: Decoder[Response] = cursor => {
    val idCursor     = cursor.downField(Field.id)
    val resultCursor = cursor.downField(Field.result)
    val errorCursor  = cursor.downField(Field.error)
    for {
      id     <- Decoder[Option[Id]].tryDecode(idCursor)
      result <- Decoder[Option[Result]].tryDecode(resultCursor)
      error  <- Decoder[Option[ResponseError]].tryDecode(errorCursor)
      response <- (result, error) match {
        case (None, Some(err)) => Right(Response.error(id, err))
        case (Some(res), None) => Right(Response.result(id, res))
        case (None, None)      => Right(Response.emptyResult(id))
        case _ =>
          Left(DecodingFailure(ErrorMessage.invalidResponse, cursor.history))
      }
    } yield response
  }
}
