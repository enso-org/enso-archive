package org.enso.languageserver.monitoring

import org.enso.jsonrpc.{HasParams, HasResult, Method, Unused}

object MonitoringApi {

  case object Ping extends Method("heartbeat/ping") {
    implicit val hasParams = new HasParams[this.type] {
      type Params = Unused.type
    }
    implicit val hasResult = new HasResult[this.type] {
      type Result = Unused.type
    }
  }

}
