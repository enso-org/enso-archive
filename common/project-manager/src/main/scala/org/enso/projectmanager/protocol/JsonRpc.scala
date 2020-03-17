package org.enso.projectmanager.protocol

import org.enso.jsonrpc.Protocol
import org.enso.projectmanager.protocol.ProjectManagementApi.ProjectCreate

object JsonRpc {

  /**
    * A description of supported JSON RPC messages.
    */
  lazy val protocol: Protocol =
    Protocol.empty
      .registerRequest(ProjectCreate)

}
