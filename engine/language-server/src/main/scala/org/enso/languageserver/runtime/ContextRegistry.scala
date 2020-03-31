package org.enso.languageserver.runtime

import java.util.UUID

import akka.actor.{Actor, ActorRef, Props, Terminated}
import org.enso.languageserver.data.ExecutionContextConfig
import org.enso.languageserver.runtime.ExecutionApi.ContextId

/**
  *
  * {{{
  *                      Request
  * +------------------+         +------------------+
  * | ClientController +-------->+ ContextRegistry  |  Client -> ActorRef
  * +--------------+---+         +---------+--------+           (ContextManager)
  *                ^                       |
  *                |                       | forward
  *                |                       v
  *                |             +---------+--------+
  *                +-------------+  ContextManager  |  [ContextId]
  *                              +----+--------+----+
  *                                   |        ^  polyglot_api
  *                                   |        |  Runtime.Api
  *                                   v        |
  *                              +----+--------+----+  Runtime.Api(reqId)
  *                              | RuntimeConnector |       ReqId  -> ActorRef
  *                              +---------+--------+                (sender)
  *                                        ^           publish Event
  *                                        | sendBinary
  *                                        v
  *                              +---------+--------+
  *                              | MessageEndpoint  |
  *                              +------------------+
  *
  * }}}
  */
final class ContextRegistry(config: ExecutionContextConfig, runtime: ActorRef) extends Actor {

  import ContextRegistry._

  override def receive: Receive =
    withStore(Map())

  // TODO: store
  private def withStore(store: Map[ClientRef, ManagerRef]): Receive = {
    case ExecutionProtocol.CreateContextRequest =>
      store.get(sender()) match {
        case Some(manager) =>
          manager.forward(ExecutionProtocol.CreateContextRequest(freshId))
        case None =>
          val manager = context.actorOf(ContextManager.props(config.requestTimeout, runtime))
          context.watch(manager)
          manager.forward(ExecutionProtocol.CreateContextRequest(freshId))
          context.become(withStore(store + (sender() -> manager)))
      }

    case Terminated(manager) =>
      context.become(withStore(store.filter(kv => kv._2 != manager)))

  }

  private def freshId: ContextId =
    UUID.randomUUID()
}

object ContextRegistry {

  private type ClientRef = ActorRef

  private type ManagerRef = ActorRef

  def props(config: ExecutionContextConfig, runtime: ActorRef): Props =
    Props(new ContextRegistry(config, runtime))
}
