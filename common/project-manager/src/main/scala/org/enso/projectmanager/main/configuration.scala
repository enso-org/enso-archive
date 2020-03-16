package org.enso.projectmanager.main

object configuration {

  case class ProjectManagerConfig(server: ServerConfig)

  case class ServerConfig(host: String, port: Int)

}
