package org.enso.interpreter.instrument.command

import org.enso.polyglot.runtime.Runtime.Api

object CommandFactory {

  def createCommand(request: Api.Request): Command =
    request.payload match {
      case payload: Api.CreateContextRequest =>
        new CreateContextCmd(request.requestId, payload)
    }

}
