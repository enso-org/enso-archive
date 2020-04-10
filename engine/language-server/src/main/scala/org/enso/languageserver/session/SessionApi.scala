package org.enso.languageserver.session

import java.util.UUID

import org.enso.jsonrpc.{HasParams, HasResult, Method}

/**
  * The connection management JSON RPC API provided by the language server.
  * See [[https://github.com/luna/enso/blob/master/doc/design/engine/engine-services.md]]
  * for message specifications.
  */
object SessionApi {

  case object InitProtocolConnection
      extends Method("session/initProtocolConnection") {

    case class Params(clientId: UUID)

    case class Result(contentRoots: Set[UUID])

    implicit val hasParams = new HasParams[this.type] {
      type Params = InitProtocolConnection.Params
    }
    implicit val hasResult = new HasResult[this.type] {
      type Result = InitProtocolConnection.Result
    }
  }

}
