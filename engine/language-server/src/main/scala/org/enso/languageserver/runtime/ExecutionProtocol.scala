package org.enso.languageserver.runtime

import org.enso.languageserver.runtime.ExecutionApi.ContextId

object ExecutionProtocol {

  sealed trait Api

  /**
    * A request to the language server to create a new execution context.
    *
    * @param contextId the newly created context's id
    */
  case class CreateContextRequest(contextId: ContextId) extends Api

  /**
    * A response about creation of a new execution context.
    *
    * @param contextId the newly created context's id
    */
  case class CreateContextResponse(contextId: ContextId) extends Api

  /**
    * A request to the language server to delete an execution context.
    *
    * @param contextId the newly created context's id
    */
  case class DestroyContextRequest(contextId: ContextId) extends Api

  /**
    * A response about deletion of an execution context.
    *
    * @param contextId the newly created context's id
    */
  case class DestroyContextResponse(contextId: ContextId) extends Api

  /**
    * Signals that user doesn't have access to the requested context.
    */
  case object AccessDeniedError extends Api

}
