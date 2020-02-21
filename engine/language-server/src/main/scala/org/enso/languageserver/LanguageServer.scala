package org.enso.languageserver
import java.io.File
import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, Stash}
import org.enso.languageserver.data.{CanEdit, CapabilityRegistration}

object LanguageProtocol {
  type ClientId = UUID
  case class Initialize(config: Config)
  case class Connect(clientId: ClientId, clientActor: ActorRef)
  case class Disconnect(clientId: ClientId)

  case class Config(contentRoots: List[File], languagePath: List[File])

  case class Client(
    id: ClientId,
    actor: ActorRef,
    capabilities: List[CapabilityRegistration]
  )

  case class Env(clients: List[Client]) {
    def addClient(client: Client): Env = {
      copy(clients = client :: clients)
    }

    def removeClient(clientId: ClientId): Env =
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
      clientId: ClientId,
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
      clientId: ClientId,
      registration: CapabilityRegistration
    ): Env =
      modifyClient(clientId, { client =>
        client.copy(capabilities = registration :: client.capabilities)
      })

    def releaseCapability(clientId: ClientId, capabilityId: UUID): Env =
      modifyClient(clientId, { client =>
        client.copy(
          capabilities = client.capabilities.filter(_.id != capabilityId)
        )
      })
  }

  object Env {
    def empty: Env = Env(List())
  }

  case class AcquireCapability(
    clientId: UUID,
    registration: CapabilityRegistration
  )
  case class ReleaseCapability(clientId: ClientId, capabilityId: UUID)
  case class ForceReleaseCapability(capabilityId: UUID)
  case class GrantCapability(registration: CapabilityRegistration)
}

class Server extends Actor with Stash with ActorLogging {
  import LanguageProtocol._

  override def receive: Receive = {
    case Initialize(config) =>
      log.debug("Language Server initialized.")
      unstashAll()
      context.become(initialized(config))
    case _ => stash()
  }

  def initialized(config: Config, env: Env = Env.empty): Receive = {
    case Connect(clientId, actor) =>
      log.debug("Client connected [{}].", clientId)
      context.become(
        initialized(config, env.addClient(Client(clientId, actor, List())))
      )
    case Disconnect(clientId) =>
      log.debug("Client disconnected [{}].", clientId)
      context.become(initialized(config, env.removeClient(clientId)))
    case AcquireCapability(
        clientId,
        reg @ CapabilityRegistration(_, capability: CanEdit)
        ) =>
      val (envWithoutCapability, releasingClients) = env.removeCapabilitiesBy {
        case CapabilityRegistration(_, CanEdit(file)) => file == capability.path
        case _                                        => false
      }
      releasingClients.foreach {
        case (client: Client, capabilities) =>
          capabilities.foreach { registration =>
            client.actor ! ForceReleaseCapability(registration.id)
          }
      }
      val newEnv = envWithoutCapability.grantCapability(clientId, reg)
      context.become(initialized(config, newEnv))
    case ReleaseCapability(clientId, capabilityId) =>
      context.become(
        initialized(config, env.releaseCapability(clientId, capabilityId))
      )
  }
}
