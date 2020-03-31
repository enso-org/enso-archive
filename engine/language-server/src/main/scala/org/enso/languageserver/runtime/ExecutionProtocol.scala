package org.enso.languageserver.runtime

import org.enso.languageserver.runtime.ExecutionApi.ContextId

object ExecutionProtocol {

  sealed trait Api

  case class CreateContextRequest(contextId: ContextId) extends Api

  case class CreateContextResponse(contextId: ContextId) extends Api

  case object ExecutionContextExistsError extends Api

}
