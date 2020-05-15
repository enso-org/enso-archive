package org.enso.languageserver.io

import org.enso.languageserver.data.ClientId

object InputOutputProtocol {

  case class RedirectOutput(clientId: ClientId)

  case class SuppressOutput(clientId: ClientId)

  case class OutputAppended(output: String, outputKind: OutputKind)

}
