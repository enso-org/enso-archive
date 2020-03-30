package org.enso.languageserver.filemanager

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import org.enso.languageserver.data.{CapabilityRegistration, Client, Config}
import org.enso.languageserver.effect._
import org.enso.languageserver.capability.CapabilityProtocol.{
  AcquireCapability,
  CapabilityAcquired,
  CapabilityAcquisitionFileSystemFailure,
  CapabilityForceReleased,
  CapabilityNotAcquiredResponse,
  CapabilityReleased,
  ReleaseCapability
}
import org.enso.languageserver.data.{
  CapabilityRegistration,
  ReceivesTreeUpdates
}
import org.enso.languageserver.event.ClientDisconnected

/**
  * FileEvent registry handles `receivesTreeUpdates` capability, starts
  * [[FileEventManager]], and handles errors
  *
  * @param config configuration
  * @param fs file system
  * @param exec executor of file system events
  */
final class FileEventRegistry(
  config: Config,
  fs: FileSystemApi[BlockingIO],
  exec: Exec[BlockingIO]
) extends Actor
    with ActorLogging {

  import FileEventRegistry._

  override def preStart(): Unit = {
    context.system.eventStream
      .subscribe(self, classOf[ClientDisconnected]): Unit
  }

  override def receive: Receive = withStore(Store())

  def withStore(store: Store): Receive = {
    case AcquireCapability(
        clientController,
        CapabilityRegistration(ReceivesTreeUpdates(path))
        ) =>
      val client  = ClientRef(clientController.actor, path)
      val handler = sender()
      if (store.hasManager(client)) {
        val oldManager = store.getManager(client)
        oldManager ! FileEventManagerProtocol.UnwatchPath
      }

      val manager = context.actorOf(FileEventManager.props(config, fs, exec))
      context.watch(manager)
      manager ! FileEventManagerProtocol.WatchPath(path, clientController.actor)
      context.become(withStore(store.addMappings(manager, client, handler)))

    case ReleaseCapability(
        clientController,
        CapabilityRegistration(ReceivesTreeUpdates(path))
        ) =>
      val client  = ClientRef(clientController.actor, path)
      val handler = sender()
      if (store.hasManager(client)) {
        val manager = store.getManager(client)
        manager ! FileEventManagerProtocol.UnwatchPath
        context.become(withStore(store.addHandler(manager, handler)))
      } else {
        handler ! CapabilityNotAcquiredResponse
      }

    case FileEventManagerProtocol.WatchPathResult(result) =>
      val manager = sender()
      if (store.hasHandler(manager)) {
        val handler = store.getHandler(manager)
        result match {
          case Right(()) =>
            handler ! CapabilityAcquired
          case Left(err) =>
            log.error(s"Error acquiring capability: $err")
            handler ! CapabilityAcquisitionFileSystemFailure(err)
            manager ! FileEventManagerProtocol.UnwatchPath
        }
        context.become(withStore(store.removeHandler(manager)))
      } else {
        log.error(s"Unable to find a handler after WatchPath")
        manager ! FileEventManagerProtocol.UnwatchPath
      }

    case FileEventManagerProtocol.UnwatchPathResult(result) =>
      val manager = sender()
      if (store.hasHandler(manager)) {
        val handler = store.getHandler(manager)
        result.foreach(err => log.error(s"Error releasing capability: $err"))
        handler ! CapabilityReleased
      }
      context.stop(manager)
      context.become(withStore(store.removeMappings(manager)))

    case FileEventManagerProtocol.FileEventError(e) =>
      val manager = sender()
      if (store.hasClient(manager)) {
        log.error(s"File watcher error, releasing capability", e)
        val client = store.getClient(manager)
        client.actor ! CapabilityForceReleased(
          CapabilityRegistration(ReceivesTreeUpdates(client.path))
        )
      } else {
        log.error("Unable to find a client for FileEventError", e)
      }
      context.become(withStore(store.removeMappings(manager)))

    case ClientDisconnected(client) =>
      store
        .getManagers(client)
        .foreach { _ ! FileEventManagerProtocol.UnwatchPath }

    case Terminated(manager) =>
      context.become(withStore(store.removeMappings(manager)))
  }
}

object FileEventRegistry {

  /**
    * [[FileEventManager]] actor
    */
  private type EventManagerRef = ActorRef

  /**
    * Acquire/ReleaseCapabilityHandler actor
    */
  private type HandlerRef = ActorRef

  /**
    * [[ClientController]] actor who requested to acquire [[ReceiveTreeUpdates]]
    * capability
    */
  private case class ClientRef(actor: ActorRef, path: Path)

  /**
    * Internal state of a [[FileEventRegistry]].
    *
    * @param managerStore a mapping between a client requested to watch a path,
    * and event manager watching the path
    * @param clientStore a mapping between an event manager watching the path,
    * and a client who requested to watch this path
    * @param handlerStore a mapping between an event manager watching the path,
    * and a capability handler who sent the AcquireCapability or
    * ReleaseCapability request
    */
  private case class Store(
    managerStore: Map[ClientRef, EventManagerRef],
    clientStore: Map[EventManagerRef, ClientRef],
    handlerStore: Map[EventManagerRef, HandlerRef]
  ) {

    /**
      * Checks if client has a manager associated with it.
      *
      * @client client controller
      */
    def hasManager(client: ClientRef): Boolean =
      managerStore.contains(client)

    /**
      * Returns manager associated with the provided client.
      *
      * @param client client controller
      * @return manager associated with this client
      */
    def getManager(client: ClientRef): EventManagerRef =
      managerStore(client)

    /**
      * Checks if manager has a handler associated with it.
      *
      * @param manager file event manager
      */
    def hasHandler(manager: EventManagerRef): Boolean =
      handlerStore.contains(manager)

    /**
      * Returns handler associated with the provided manager.
      *
      * @param manager file event manager
      * @return handler associated with this manager
      */
    def getHandler(manager: EventManagerRef): HandlerRef =
      handlerStore(manager)

    /**
      * Checks if manager has a client associated with it.
      *
      * @param manager file event manager
      */
    def hasClient(manager: EventManagerRef): Boolean =
      clientStore.contains(manager)

    /**
      * Returns client associated with the provided manager.
      *
      * @param manaer file event manager
      * @return client associated with this manager
      */
    def getClient(manager: EventManagerRef): ClientRef =
      clientStore(manager)

    /**
      * Get all managers associated with a client.
      */
    def getManagers(client: Client): Vector[EventManagerRef] =
      managerStore.view
        .filterKeys(_.actor == client.actor)
        .values
        .toVector

    /**
      * Add manager and associated mappings to the store.
      *
      * @param manager [[FileEventManager]] event manager
      * @param client client controller
      * @param handler Acquire or Release capability handler
      * @return updated store
      */
    def addMappings(
      manager: EventManagerRef,
      client: ClientRef,
      handler: HandlerRef
    ): Store =
      copy(
        managerStore = managerStore + (client  -> manager),
        clientStore  = clientStore + (manager  -> client),
        handlerStore = handlerStore + (manager -> handler)
      )

    /**
      * Remove manager and associated mappings from the store.
      *
      * @param manager [[FileEventManager]] event manager
      * @return updated store
      */
    def removeMappings(manager: EventManagerRef): Store = {
      val newManagerStore = clientStore
        .get(manager)
        .map(managerStore - _)
        .getOrElse(managerStore)
      copy(
        managerStore = newManagerStore,
        clientStore  = clientStore - manager,
        handlerStore = handlerStore - manager
      )
    }

    /**
      * Add new manager with associated handler to the store.
      *
      * @param manager [[FileEventManager]] event manager
      * @param handler Acquire or Release capability handler
      * @return updated store
      */
    def addHandler(manager: EventManagerRef, handler: HandlerRef): Store =
      copy(handlerStore = handlerStore + (manager -> handler))

    /**
      * Remove manager and associated handler from the store.
      *
      * @param manager [[FileEventManager]] event manager.
      * @return updated store
      */
    def removeHandler(manager: EventManagerRef): Store =
      copy(handlerStore = handlerStore - manager)
  }

  private object Store {

    def apply(): Store =
      new Store(Map(), Map(), Map())
  }

  /**
    * Creates a configuration object used to create a [[FileEventRegistry]].
    *
    * @param config configuration
    * @param exec executor of file system events
    */
  def props(
    config: Config,
    fs: FileSystemApi[BlockingIO],
    exec: Exec[BlockingIO]
  ): Props =
    Props(new FileEventRegistry(config, fs, exec))
}
