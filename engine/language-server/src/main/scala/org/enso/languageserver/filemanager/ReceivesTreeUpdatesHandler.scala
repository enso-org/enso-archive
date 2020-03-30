package org.enso.languageserver.filemanager

import akka.actor.{Actor, ActorRef, Props, Terminated}
import org.enso.languageserver.capability.CapabilityProtocol.{
  AcquireCapability,
  CapabilityNotAcquiredResponse,
  ReleaseCapability
}
import org.enso.languageserver.data.{CapabilityRegistration, Config}
import org.enso.languageserver.data.{
  CapabilityRegistration,
  ReceivesTreeUpdates
}
import org.enso.languageserver.effect._

/**
  * Handles `receivesTreeUpdates` capabilities acquisition and release.
  *
  * @param config configuration
  * @param fs file system
  * @param exec executor of file system events
  */
final class ReceivesTreeUpdatesHandler(
  config: Config,
  fs: FileSystemApi[BlockingIO],
  exec: Exec[BlockingIO]
) extends Actor {

  import ReceivesTreeUpdatesHandler._

  override def receive: Receive = withStore(Store())

  def withStore(store: Store): Receive = {
    case AcquireCapability(
        client,
        CapabilityRegistration(ReceivesTreeUpdates(path))
        ) =>
      store.getManager(path) match {
        case Some(manager) =>
          manager.forward(
            FileEventManagerProtocol.WatchPath(path, client.actor)
          )
        case None =>
          val manager =
            context.actorOf(FileEventManager.props(config, fs, exec))
          context.watch(manager)
          manager.forward(
            FileEventManagerProtocol.WatchPath(path, client.actor)
          )
          context.become(withStore(store.addManager(manager, path)))
      }

    case ReleaseCapability(
        client,
        CapabilityRegistration(ReceivesTreeUpdates(path))
        ) =>
      store.getManager(path) match {
        case Some(manager) =>
          manager.forward(FileEventManagerProtocol.UnwatchPath(client.actor))
        case None =>
          sender() ! CapabilityNotAcquiredResponse
      }

    case Terminated(manager) =>
      context.become(withStore(store.removeManager(manager)))
  }
}

object ReceivesTreeUpdatesHandler {

  /**
    * Internal state of a [[ReceivesTreeUpdatesHandler]].
    *
    * @param managers a file event manager with a watched path
    */
  case class Store(managers: Map[Path, ActorRef]) {

    /**
      * Returns manager associated with the provided path.
      *
      * @param path watched path
      * @return optional manager associated with this path
      */
    def getManager(path: Path): Option[ActorRef] =
      managers.get(path)

    /**
      * Add new manager with watched path to the store.
      *
      * @param manager file event manager
      * @param path watched path
      * @return updated store
      */
    def addManager(manager: ActorRef, path: Path): Store =
      copy(managers = managers + (path -> manager))

    /**
      * Remove manager from the store.
      *
      * @param manager file event manager
      * @return updated store
      */
    def removeManager(manager: ActorRef): Store =
      copy(managers = managers.filter(kv => kv._2 != manager))
  }

  private object Store {

    def apply(): Store =
      new Store(Map())
  }

  /**
    * Creates a configuration object used to create a
    * [[ReceivesTreeUpdatesHandler]].
    *
    * @param config configuration
    * @param exec executor of file system events
    */
  def props(
    config: Config,
    fs: FileSystemApi[BlockingIO],
    exec: Exec[BlockingIO]
  ): Props =
    Props(new ReceivesTreeUpdatesHandler(config, fs, exec))
}
