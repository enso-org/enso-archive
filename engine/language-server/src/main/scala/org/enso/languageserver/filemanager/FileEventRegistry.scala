package org.enso.languageserver.filemanager

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import org.enso.languageserver.data.Config
import org.enso.languageserver.effect._
import org.enso.languageserver.capability.CapabilityProtocol.{
  AcquireCapability,
  CapabilityAcquisitionBadRequest,
  CapabilityAcquired,
  CapabilityReleaseBadRequest,
  CapabilityReleased,
  ReleaseCapability
}
import org.enso.languageserver.data.{
  CapabilityRegistration,
  ReceivesTreeUpdates
}

/**
  * FileEvent registry handles [[ReceivesTreeUpdates]] capabilities, starts
  * [[FileEventManager]], handles errors, and sends file events to the
  * [[ClientController]]
  *
  * ==Implementation==
  *
  * Scheme of interaction between file-event actors:
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
  *
  * @param config configuration
  * @param exec executor of file system events
  */
final class FileEventRegistry(config: Config, exec: Exec[BlockingIO])
    extends Actor
    with ActorLogging {

  import FileEventRegistry._

  override def receive: Receive = withRegistry(Map(), Map(), Map())

  def withRegistry(
    watcherStore: Map[RegisteredClient, EventManagerRef],
    clientStore: Map[EventManagerRef, RegisteredClient],
    handlerStore: Map[EventManagerRef, ActorRef]
  ): Receive = {
    case AcquireCapability(
        client,
        CapabilityRegistration(ReceivesTreeUpdates(path))
        ) =>
      val registeredClient = RegisteredClient(client.actor, path)
      if (watcherStore.contains(registeredClient)) {
        sender() ! CapabilityAcquisitionBadRequest
      } else {
        val eventManager =
          context.actorOf(FileEventManager.props(config, exec))
        eventManager ! FileEventManagerProtocol.WatchPath(path)
        val newWatcherStore = watcherStore
          .updated(registeredClient, eventManager)
        val newClientStore = clientStore
          .updated(eventManager, registeredClient)
        val newHandlerStore = handlerStore
          .updated(eventManager, sender())
        context.become(withRegistry(newWatcherStore, newClientStore, newHandlerStore))
      }

    case ReleaseCapability(
        client,
        CapabilityRegistration(ReceivesTreeUpdates(path))
        ) =>
      val registeredClient = RegisteredClient(client.actor, path)
      if (watcherStore.contains(registeredClient)) {
        val eventManager = watcherStore(registeredClient)
        eventManager ! FileEventManagerProtocol.UnwatchPath
        val newHandlerStore = handlerStore + (eventManager -> sender())
        context.become(withRegistry(watcherStore, clientStore, newHandlerStore))
      } else {
        sender() ! CapabilityReleaseBadRequest
      }

    case FileEventManagerProtocol.WatchPathResult(result) =>
      if (handlerStore.contains(sender())) {
        val handler = handlerStore(sender())
        result match {
          case Right(()) =>
            handler ! CapabilityAcquired
          case Left(err) =>
            log.error(s"Error acquiring capability: $err")
            handler ! CapabilityAcquisitionBadRequest
            sender() ! FileEventManagerProtocol.UnwatchPath
        }
        val newHandlerStore = handlerStore - sender()
        context.become(withRegistry(watcherStore, clientStore, newHandlerStore))
      } else {
        log.error(s"Unable to find a client after WatchPath")
        sender() ! FileEventManagerProtocol.UnwatchPath
      }

    case FileEventManagerProtocol.UnwatchPathResult(result) =>
      if (handlerStore.contains(sender())) {
        val handler = handlerStore(sender())
        result match {
          case Right(()) =>
            handler ! CapabilityReleased
          case Left(err) =>
            log.error(s"Error releasing capability: $err")
            handler ! CapabilityReleaseBadRequest
        }
        context.stop(sender())
        val newWatcherStore = clientStore.get(sender()).map(watcherStore - _).getOrElse(watcherStore)
        val newClientStore = clientStore - sender()
        val newHandlerStore = handlerStore - sender()
        context.become(withRegistry(newWatcherStore, newClientStore, newHandlerStore))
      } else {
        // Reply has already been sent, cleanup the resources
        context.stop(sender())
      }

    case msg @ FileEventManagerProtocol.FileEventResult(_) =>
      if (clientStore.contains(sender())) {
        val client = clientStore(sender())
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

  /**
    * Creates a configuration object used to create a [[FileEventRegistry]].
    *
    * @param config configuration
    * @param exec executor of file system events
    */
  def props(config: Config, exec: Exec[BlockingIO]): Props =
    Props(new FileEventRegistry(config, exec))
}
