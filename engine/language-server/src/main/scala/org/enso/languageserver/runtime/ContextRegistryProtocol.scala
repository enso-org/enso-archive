package org.enso.languageserver.runtime

import akka.actor.ActorRef
import org.enso.languageserver.runtime.ExecutionApi.ContextId

object ContextRegistryProtocol {

  /**
    * A request to the context registry to create a new execution context.
    *
    * @param client reference to the client
    */
  case class CreateContextRequest(client: ActorRef)

  /**
    * A request to the context registry to delete an execution context.
    *
    * @param client reference to the client
    */
  case class DestroyContextRequest(client: ActorRef, contextId: ContextId)

}
