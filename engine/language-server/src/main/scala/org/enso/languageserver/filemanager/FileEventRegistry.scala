package org.enso.languageserver.filemanager

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import org.enso.languageserver.data.{Client, Config}
import org.enso.languageserver.effect._
import org.enso.languageserver.capability.CapabilityProtocol.{
  AcquireCapability,
  CapabilityAcquired,
  CapabilityAcquisitionBadRequest,
  CapabilityReleased,
  CapabilityReleaseBadRequest,
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
  *              +-------------------+
  *              |      Client       |
  *              +-------------------+
  *                    ^      ^ CapabilityResponse
  *          FileEvent |      |
  *                    |      v Acquire/ReleaseCapability
  *              +-------------------+
  *              | FileEventRegistry |
  *              +-------------------+
  *                    ^      ^ Watch/UnwatchPathResult
  *    FileEventResult |      |
  *                    |      v Watch/UnwatchPath
  *              +-------------------+
  *              | FileEventManager  |
  *              +-------------------+
  *                    ^
  *       WatcherEvent |
  *                    |
  *              +-------------------+
  *              | FileEventWatcher  |
  *              +-------------------+
  *
  * }}}
  */
final class FileEventRegistry(
  config: Config,
  fs: FileSystemApi[BlockingIO],
  exec: Exec[BlockingIO]
) extends Actor with ActorLogging {

  import FileEventRegistry._

  override def receive: Receive = withRegistry(Map(), Map())

  def withRegistry(
    watcherRegistry: Map[RegisteredClient, EventManagerRef],
    clientRegistry: Map[EventManagerRef, ClientRef]
  ): Receive = {
    case AcquireCapability(
        client,
        CapabilityRegistration(ReceivesTreeUpdates(path))
        )  =>
      if (watcherRegistry.contains(RegisteredClient(client.id, path))) {
        sender() ! CapabilityAcquisitionBadRequest
      } else {
        val eventManager =
          context.actorOf(FileEventManager.props(config, fs, exec))
        eventManager ! FileEventManagerProtocol.WatchPath(path)
        val newWatcherRegistry = watcherRegistry
          .updated(RegisteredClient(client.id, path), eventManager)
        val newClientRegistry = clientRegistry
          .updated(eventManager, client.actor)
        context.become(withRegistry(newWatcherRegistry, newClientRegistry))
    }

    case ReleaseCapability(
        clientId,
        CapabilityRegistration(ReceivesTreeUpdates(path))
        ) =>
      if (watcherRegistry.contains(RegisteredClient(clientId, path))) {
        val eventManager = watcherRegistry(RegisteredClient(clientId, path))
        eventManager ! FileEventManagerProtocol.UnwatchPath
        val newWatcherRegistry =
          watcherRegistry - RegisteredClient(clientId, path)
        val newClientRegistry =
          clientRegistry - eventManager
        context.become(withRegistry(newWatcherRegistry, newClientRegistry))
      } else {
        sender() ! CapabilityReleaseBadRequest
      }

    case FileEventManagerProtocol.WatchPathResult(result) =>
      if (clientRegistry.contains(sender())) {
        val clientRef = clientRegistry(sender())
        result match {
          case Right(()) =>
            clientRef ! CapabilityAcquired
          case Left(err) =>
            log.error(s"Error acquiring capability: $err")
            clientRef ! CapabilityAcquisitionBadRequest
            sender() ! FileEventManagerProtocol.UnwatchPath
        }
      } else {
        log.error(s"Unable to find a client after WatchPath")
        sender() ! FileEventManagerProtocol.UnwatchPath
      }

    case FileEventManagerProtocol.UnwatchPathResult(result) =>
      if (clientRegistry.contains(sender())) {
        val clientRef = clientRegistry(sender())
        result match {
          case Right(()) =>
            clientRef ! CapabilityReleased
          case Left(err) =>
            log.error(s"Error releasing capability: $err")
            clientRef ! CapabilityReleaseBadRequest
        }
      } else {
        log.error(s"Unable to find a client after UnwatchPath")
      }
      context.stop(sender())

    case msg @ FileEventManagerProtocol.FileEventResult(result) =>
      if (clientRegistry.contains(sender())) {
        val clientRef = clientRegistry(sender())
        result match {
          case Right(event) =>
            // TODO: Notification
            clientRef ! FileManagerApi.EventFile.Result(event)
          case Left(error) =>
            log.error(s"Failed to process file event: $error")
        }
      } else {
        log.error(s"Unable to find a client for $msg")
        sender() ! FileEventManagerProtocol.UnwatchPath
      }
  }
}

object FileEventRegistry {

  private type EventManagerRef = ActorRef

  private type ClientRef = ActorRef

  private case class RegisteredClient(clientId: Client.Id, path: Path)

  def props(
    config: Config,
    fs: FileSystemApi[BlockingIO],
    exec: Exec[BlockingIO]
  ): Props =
    Props(new FileEventRegistry(config, fs, exec))
}
