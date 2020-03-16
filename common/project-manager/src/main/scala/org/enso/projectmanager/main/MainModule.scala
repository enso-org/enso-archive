package org.enso.projectmanager.main

import akka.actor.ActorSystem
import akka.stream.SystemMaterializer
import org.enso.jsonrpc.{JsonRpcServer, Protocol}
import org.enso.projectmanager.main.configuration.ProjectManagerConfig
import org.enso.projectmanager.protocol.ManagerClientControllerFactory

class MainModule(config: ProjectManagerConfig) {

  implicit val system = ActorSystem()

  implicit val materializer = SystemMaterializer.get(system)

  lazy val protocol: Protocol = Protocol.empty

  lazy val clientControllerFactory = new ManagerClientControllerFactory(system)

  lazy val server = new JsonRpcServer(protocol, clientControllerFactory)

}
