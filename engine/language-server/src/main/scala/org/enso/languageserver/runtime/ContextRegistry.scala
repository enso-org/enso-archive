package org.enso.languageserver.runtime

import akka.actor.{Actor, ActorRef, Props, Terminated}
import org.enso.languageserver.data.ExecutionContextConfig

/**
  * Registry handles execution context requests and routes them to the
  * appropriate context manager.
  *
  * == Implementation ==
  *
  * Legend:
  *
  *   - 1  - Singleton
  *   - *C - Created per client
  *   - *H - Request is forwarded to intermediate handler. Created per request.
  *
  * {{{
  *
  *                   *C                            1
  *  +------------------+   *H    +------------------+
  *  | ClientController +-------->+ ContextRegistry  |
  *  +--------------+---+         +---------+--------+
  *                 ^                       |
  *                 |                       |
  *                 |                       v      *C
  *                 |             +---------+--------+
  *                 +-------------+  ContextManager  |
  *                               +---------+--------+
  *                                         ^
  *                                         |*H
  *                                         v       1
  *                               +---------+--------+
  *                               | RuntimeConnector |
  *                               +------------------+
  *
  * }}}
  *
  * @param config execution context configuration
  * @param runtime reference to the [[RuntimeConnector]]
  */
final class ContextRegistry(config: ExecutionContextConfig, runtime: ActorRef)
    extends Actor {

  import ContextRegistry._

  override def receive: Receive =
    withStore(Map())

  private def withStore(store: Map[ClientRef, ManagerRef]): Receive = {
    case ContextRegistryProtocol.CreateContextRequest(client) =>
      store.get(client) match {
        case Some(manager) =>
          manager.forward(ExecutionProtocol.CreateContextRequest)
        case None =>
          val manager = context.actorOf(
            ContextManager.props(config.requestTimeout, runtime)
          )
          context.watch(manager)
          manager.forward(ExecutionProtocol.CreateContextRequest)
          context.become(withStore(store + (sender() -> manager)))
      }

    case Terminated(manager) =>
      context.become(withStore(store.filter(kv => kv._2 != manager)))
  }
}

object ContextRegistry {

  private type ClientRef = ActorRef

  private type ManagerRef = ActorRef

  /**
    * Creates a configuration object used to create a [[ContextRegistry]].
    *
    * @param config execution context configuration
    * @param runtime reference to the [[RuntimeConnector]]
    */
  def props(config: ExecutionContextConfig, runtime: ActorRef): Props =
    Props(new ContextRegistry(config, runtime))
}
