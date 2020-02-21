package org.enso.languageserver.data
import java.util.UUID

import akka.actor.ActorRef

case class Client(
  id: Client.Id,
  actor: ActorRef,
  capabilities: List[CapabilityRegistration]
)

object Client {
  type Id = UUID
  def apply(id: Id, actor: ActorRef): Client = Client(id, actor, List())
}
