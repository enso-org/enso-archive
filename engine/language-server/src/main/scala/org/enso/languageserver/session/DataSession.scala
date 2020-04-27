package org.enso.languageserver.session

import akka.actor.ActorRef
import org.enso.languageserver.data.ClientId

case class DataSession(clientId: ClientId, dataController: ActorRef)
