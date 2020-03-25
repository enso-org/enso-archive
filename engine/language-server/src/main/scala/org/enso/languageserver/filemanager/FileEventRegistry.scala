package org.enso.languageserver.filemanager

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import org.enso.languageserver.data.Config
import org.enso.languageserver.effect._
import org.enso.languageserver.capability.CapabilityProtocol.{
  AcquireCapability,
  CapabilityAcquisitionBadRequest,
  CapabilityGranted,
  CapabilityReleaseBadRequest,
  CapabilityReleased,
  ReleaseCapability
}
import org.enso.languageserver.data.{
  CapabilityRegistration,
  ReceivesTreeUpdates
}

/**
  * FileEvent Registry
  *
  * ==Scheme==
  *
  * {{{
  *
  *  +------------------+  +-------------------+
  *  | ClientController |  | CapabilityHandler |
  *  +------------------+  +-------------------+
  *                   ^      ^ CapabilityResponse
  *   FileEventResult |      |
  *                   |      v Acquire/ReleaseCapability
  *             +-------------------+
  *             | FileEventRegistry |
  *             +-------------------+
  *                   ^      ^ Watch/UnwatchPathResult
  *         FileEvent |      |
  *                   |      v Watch/UnwatchPath
  *             +-------------------+
  *             | FileEventManager  |
  *             +-------------------+
  *                   ^
  *      WatcherEvent |
  *                   |
  *             +-------------------+
  *             | FileEventWatcher  |
  *             +-------------------+
  *
  * }}}
  */
final class FileEventRegistry(config: Config, exec: Exec[BlockingIO])
    extends Actor
    with ActorLogging {

  import FileEventRegistry._

  override def receive: Receive = withRegistry(Map(), Map())

  def withRegistry(
    watcherRegistry: Map[RegisteredClient, EventManagerRef],
    clientRegistry: Map[EventManagerRef, RegisteredClient]
  ): Receive = {
    case AcquireCapability(
        client,
        CapabilityRegistration(ReceivesTreeUpdates(path))
        ) =>
      val registeredClient = RegisteredClient(client.actor, path)
      if (watcherRegistry.contains(registeredClient)) {
        sender() ! CapabilityAcquisitionBadRequest
      } else {
        val eventManager =
          context.actorOf(FileEventManager.props(config, exec))
        eventManager ! FileEventManagerProtocol.WatchPath(path)
        val newWatcherRegistry = watcherRegistry
          .updated(registeredClient, eventManager)
        val newClientRegistry = clientRegistry
          .updated(eventManager, registeredClient)
        context.become(withRegistry(newWatcherRegistry, newClientRegistry))
      }

    case ReleaseCapability(
        client,
        CapabilityRegistration(ReceivesTreeUpdates(path))
        ) =>
      val registeredClient = RegisteredClient(client.actor, path)
      if (watcherRegistry.contains(registeredClient)) {
        val eventManager = watcherRegistry(registeredClient)
        eventManager ! FileEventManagerProtocol.UnwatchPath(sender())
      } else {
        sender() ! CapabilityReleaseBadRequest
      }

    case FileEventManagerProtocol.WatchPathResult(result) =>
      if (clientRegistry.contains(sender())) {
        val client = clientRegistry(sender())
        result match {
          case Right(()) =>
            client.actor ! CapabilityGranted(
              CapabilityRegistration(ReceivesTreeUpdates(client.path))
            )
          case Left(err) =>
            log.error(s"Error acquiring capability: $err")
            client.actor ! CapabilityAcquisitionBadRequest
            sender() ! FileEventManagerProtocol.UnwatchPath
        }
      } else {
        log.error(s"Unable to find a client after WatchPath")
        sender() ! FileEventManagerProtocol.UnwatchPath
      }

    case FileEventManagerProtocol.UnwatchPathResult(handler, result) =>
      if (clientRegistry.contains(sender())) {
        result match {
          case Right(()) =>
            handler ! CapabilityReleased
          case Left(err) =>
            log.error(s"Error releasing capability: $err")
            handler ! CapabilityReleaseBadRequest
        }
        context.stop(sender())
        val newWatcherRegistry =
          watcherRegistry - clientRegistry(sender())
        val newClientRegistry =
          clientRegistry - sender()
        context.become(withRegistry(newWatcherRegistry, newClientRegistry))
      } else {
        log.error(s"Unable to find a client after UnwatchPath")
        context.stop(sender())
      }

    case msg @ FileEventManagerProtocol.FileEventResult(_) =>
      if (clientRegistry.contains(sender())) {
        val client = clientRegistry(sender())
        client.actor ! msg
      } else {
        log.error(s"Unable to find a client for $msg")
        sender() ! FileEventManagerProtocol.UnwatchPath
      }
  }
}

object FileEventRegistry {

  private type EventManagerRef = ActorRef

  private case class RegisteredClient(actor: ActorRef, path: Path)

  def props(config: Config, exec: Exec[BlockingIO]): Props =
    Props(new FileEventRegistry(config, exec))
}
