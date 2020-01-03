package org.enso

import org.enso.gateway.{Protocol, Server}

case class Gateway(languageServer: LanguageServer) extends Server with Protocol {
  import Protocol._

  def handleRequest(request: RequestOrNotification): ResponseMessage = {
    request match {
      case Initialize(id, _) => ResponseMessage(id = Some(id), result = Some(InitializeResult(ServerCapabilities())))
//      case Request(_, _, _, Some(id)) =>
//        Response.success(result = Result(), id = id)
//      case Request(_, _, _, None) => ??? // TODO: no response for Notification
      case _ =>
        throw new Exception(s"unimplemented request: $request")
    }
  }
}