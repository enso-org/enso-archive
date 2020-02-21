package org.enso.languageserver
import akka.actor.{Actor, ActorLogging, ActorRef, Stash}
import org.enso.languageserver.data._

object LanguageProtocol {
  case class Initialize(config: Config)
  case class Connect(clientId: Client.Id, clientActor: ActorRef)
  case class Disconnect(clientId: Client.Id)

  case class AcquireCapability(
    clientId: Client.Id,
    registration: CapabilityRegistration
  )
  case class ReleaseCapability(
    clientId: Client.Id,
    capabilityId: CapabilityRegistration.Id
  )
  case class ForceReleaseCapability(capabilityId: CapabilityRegistration.Id)
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
        initialized(config, env.addClient(Client(clientId, actor)))
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
