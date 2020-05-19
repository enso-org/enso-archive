package org.enso.languageserver.event

import java.util.UUID

import org.enso.languageserver.data.ClientId

sealed trait ExecutionContextEvent extends Event

case class ExecutionContextCreated(contextId: UUID, owner: ClientId)
    extends ExecutionContextEvent

case class ExecutionContextDestroyed(contextId: UUID, owner: ClientId)
    extends ExecutionContextEvent
