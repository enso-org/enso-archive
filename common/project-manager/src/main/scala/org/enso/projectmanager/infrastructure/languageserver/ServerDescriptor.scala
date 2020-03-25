package org.enso.projectmanager.infrastructure.languageserver

import java.util.UUID

import org.enso.projectmanager.boot.configuration.NetworkConfig

case class ServerDescriptor(
  name: String,
  rootId: UUID,
  root: String,
  networkConfig: NetworkConfig
)
