package org.enso.languageserver.data
import java.io.File

case class Config(contentRoots: List[File], languagePath: List[File])

object Config {
  def apply(): Config = Config(List(), List())
}

case class Env(clients: List[Client]) {
  def addClient(client: Client): Env = {
    copy(clients = client :: clients)
  }

  def removeClient(clientId: Client.Id): Env =
    copy(clients = clients.filter(_.id != clientId))

  def removeCapabilitiesBy(
    predicate: CapabilityRegistration => Boolean
  ): (Env, List[(Client, List[CapabilityRegistration])]) = {
    val newClients = clients.map { client =>
      val (removedCapabilities, retainedCapabilities) =
        client.capabilities.partition(predicate)
      val newClient = client.copy(capabilities = retainedCapabilities)
      (newClient, removedCapabilities)
    }
    (copy(clients = newClients.map(_._1)), newClients)
  }

  def modifyClient(
    clientId: Client.Id,
    modification: Client => Client
  ): Env = {
    val newClients = clients.map { client =>
      if (client.id == clientId) {
        modification(client)
      } else {
        client
      }
    }
    copy(clients = newClients)
  }

  def grantCapability(
    clientId: Client.Id,
    registration: CapabilityRegistration
  ): Env =
    modifyClient(clientId, { client =>
      client.copy(capabilities = registration :: client.capabilities)
    })

  def releaseCapability(
    clientId: Client.Id,
    capabilityId: CapabilityRegistration.Id
  ): Env =
    modifyClient(clientId, { client =>
      client.copy(
        capabilities = client.capabilities.filter(_.id != capabilityId)
      )
    })
}

object Env {
  def empty: Env = Env(List())
}
