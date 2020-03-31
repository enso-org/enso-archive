package org.enso.languageserver.runtime

import java.util.UUID

import org.enso.jsonrpc.{HasParams, HasResult, Method, Unused}
import org.enso.languageserver.data.CapabilityRegistration

object ExecutionApi {

  type ContextId = UUID

  case object ExecutionContextCreate extends Method("executionContext/create") {

    case class Result(canModify: CapabilityRegistration, receivesEvents: CapabilityRegistration)

    implicit val hasParams = new HasParams[this.type] {
      type Params = Unused.type
    }
    implicit val hasResult = new HasResult[this.type] {
      type Result = ExecutionContextCreate.Result
    }
  }
}
