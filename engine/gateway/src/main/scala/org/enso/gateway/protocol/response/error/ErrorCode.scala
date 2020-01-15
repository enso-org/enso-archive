package org.enso.gateway.protocol.response.error

object ErrorCode {
  // Defined by JSON RPC
  val parseError: Int           = -32700
  val invalidRequest: Int       = -32600
  val methodNotFound: Int       = -32601
  val invalidParams: Int        = -32602
  val internalError: Int        = -32603
  val serverErrorStart: Int     = -32099
  val serverErrorEnd: Int       = -32000
  val serverNotInitialized: Int = -32002
  val unknownErrorCode: Int     = -32001

  // Defined by the protocol
  val requestCancelled: Int = -32800
  val contentModified: Int  = -32801

  // initialize
  val unknownProtocolVersion: Int = 1
}
