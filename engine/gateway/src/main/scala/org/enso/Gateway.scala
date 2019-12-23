package org.enso

import org.enso.gateway.{Protocol, Server}

case class Gateway(languageServer: LanguageServer) extends Server with Protocol {
  import Protocol._

  def handleRequest(request: Request): Response = {
    request match {
      case GatewayRequest() =>
        Protocol.Success()
      case _ =>
        throw new Exception(f"unimplemented request: $request")
    }
  }
}