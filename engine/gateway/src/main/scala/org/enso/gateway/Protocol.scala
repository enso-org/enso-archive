package org.enso.gateway

import io.circe.{Encoder, Printer}
import io.circe.parser._
import io.circe.generic.extras.Configuration
import io.circe.syntax._
import org.enso.gateway.protocol._
import org.enso.gateway.Protocol.ShapesDerivation

/**
  * Helper for implementing protocol over text-based transport.
  * Requests and responses are marshaled as text using JSON-RPC.
  */
trait Protocol {

  /**
    * Generate a [[Response]] for a given [[Request]], no [[Response]] for a [[Notification]].
    */
  def reply(
    requestOrNotification: RequestOrNotification
  ): Option[Response]

  /**
    * See [[Server.getTextOutput]]
    */
  def getTextOutput(input: String): Option[String] = {
    import ShapesDerivation._
    decode[RequestOrNotification](input) match {
      case Left(err) => throw err
      case Right(requestOrNotification: RequestOrNotification) =>
        for {
          response <- reply(requestOrNotification)
        } yield {
          val jsonPrinter = Printer.noSpaces.copy(dropNullValues = true)
          response.asJson.printWith(jsonPrinter)
        }
    }
  }
}

object Protocol {

  /**
    * JSON-RPC Version must be 2.0.
    * LSP Spec: https://microsoft.github.io/language-server-protocol/specifications/specification-3-15/#contentPart
    */
  val jsonRpcVersion = "2.0"

  /**
    * Used in [[org.enso.gateway.protocol.request.Params.InitializeParams]]
    */
  type DocumentUri = String

  /**
    * Circe configuration for deriving codecs of [[RequestOrNotification]]
    */
  object DerivationConfig {
    private val discriminator = "method"
    implicit val configuration: Configuration =
      Configuration.default.withDiscriminator(discriminator)
  }

  /**
    * Circe Encoders for sealed traits
    */
  object ShapesDerivation {

    import shapeless.{Coproduct, Generic}

    implicit def sealedTraitEncoder[A, Repr <: Coproduct](
      implicit
      generic: Generic.Aux[A, Repr],
      coproductEncoder: Encoder[Repr]
    ): Encoder[A] = coproductEncoder.contramap(generic.to)
  }

}
