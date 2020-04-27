package org.enso.languageserver.session

import org.enso.languageserver.data.ClientId

case class Session(
  clientId: ClientId,
  maybeRpcSession: Option[RpcSession],
  maybeDataSession: Option[DataSession]
) {

  def attachRpcSession(rpcSession: RpcSession): Session = ???

  def attachDataSession(dataSession: DataSession): Session = ???

  def detachRpcSession(): Session = ???

  def detachDataSession(): Session = ???

  def isSessionTerminated: Boolean =
    maybeRpcSession.isEmpty && maybeDataSession.isEmpty

}
