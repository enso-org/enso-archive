package org.enso.gateway.protocol

import org.enso.gateway.protocol.response.Result.ApplyWorkspaceEditResult

object ResponsesFromClient {

  object ApplyWorkspaceEdit {
    def unapply(response: Response): Option[(Id, ApplyWorkspaceEditResult)] =
      (response.id, response.result) match {
        case (Some(id), Some(result: ApplyWorkspaceEditResult)) =>
          Some((id, result))
        case _ => None
      }
  }

}
