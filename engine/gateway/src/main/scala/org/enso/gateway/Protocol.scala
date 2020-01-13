package org.enso.gateway

import io.circe.Printer
import io.circe.parser._
import io.circe.syntax._
import org.enso.gateway.protocol._

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
    decode[RequestOrNotification](input) match {
      case Left(err) => throw err
      case Right(requestOrNotification: RequestOrNotification) =>
        reply(requestOrNotification).map(
          _.asJson.printWith(
            Printer.noSpaces.copy(dropNullValues = true)
          )
        )
    }
  }
}

object Protocol {

  /**
    * A string specifying the version of the JSON-RPC protocol. MUST be exactly "2.0".
    *
    * @see LSP Spec: https://microsoft.github.io/language-server-protocol/specifications/specification-3-15/#contentPart
    * @see JSON-RPC Spec: https://www.jsonrpc.org/specification#request_object
    */
  val jsonRpcVersion = "2.0"
}
