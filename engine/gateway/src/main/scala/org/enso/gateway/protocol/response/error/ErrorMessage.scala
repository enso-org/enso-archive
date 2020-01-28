package org.enso.gateway.protocol.response.error

/** Messages of [[org.enso.gateway.protocol.response.ResponseError]]. */
object ErrorMessage {
  val serverNotInitialized = "Server not initialized"
  val invalidRequest       = "Invalid request"
  val invalidJson          = "Invalid JSON"
  val wrongJsonRpcVersion  = "Wrong JSON-RPC version"
  val methodNotFound       = "Method not found"
  val unexpectedError      = "Unexpected error"
}
