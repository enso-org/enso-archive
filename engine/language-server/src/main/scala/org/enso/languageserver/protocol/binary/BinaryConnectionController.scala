package org.enso.languageserver.protocol.binary

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, Stash}
import akka.http.scaladsl.model.RemoteAddress
import com.google.flatbuffers.FlatBufferBuilder
import org.enso.languageserver.http.server.WebSocketControlProtocol.{
  ConnectionClosed,
  OutboundStreamEstablished
}
import org.enso.languageserver.protocol.binary.envelope.InboundPayload.SESSION_INIT
import org.enso.languageserver.protocol.binary.envelope.{
  InboundMessage,
  OutboundPayload
}
import org.enso.languageserver.protocol.binary.factory.{
  ErrorFactory,
  OutboundMessageFactory,
  SessionInitResponseFactory
}
import org.enso.languageserver.protocol.binary.session.SessionInit
import org.enso.languageserver.util.UnhandledLogging
import org.enso.languageserver.util.binary.DecodingFailure
import org.enso.languageserver.util.binary.DecodingFailure.{
  DataCorrupted,
  EmptyPayload,
  GenericDecodingFailure
}

class BinaryConnectionController(maybeIp: Option[RemoteAddress.IP])
    extends Actor
    with Stash
    with ActorLogging
    with UnhandledLogging {

  override def receive: Receive = connectionNotEstablished

  private def connectionNotEstablished: Receive = {
    case OutboundStreamEstablished(outboundChannel) =>
      log.debug(s"Connection established [$maybeIp]")
      unstashAll()
      context.become(connected(outboundChannel))

    case _ => stash()
  }

  private def connected(outboundChannel: ActorRef): Receive = {
    case Right(msg: InboundMessage) if msg.payloadType() == SESSION_INIT =>
      val payload = msg.payload(new SessionInit).asInstanceOf[SessionInit]
      val clientID =
        new UUID(
          payload.identifier().mostSigBits(),
          payload.identifier().leastSigBits()
        )

      implicit val builder = new FlatBufferBuilder(1024)
      val outMsg = OutboundMessageFactory.create(
        UUID.randomUUID(),
        Some(msg.requestId()),
        OutboundPayload.SESSION_INIT_RESPONSE,
        SessionInitResponseFactory.create()
      )
      builder.finish(outMsg)
      outboundChannel ! builder.dataBuffer()
      context.become(initialized(outboundChannel, clientID))

    case Left(decodingFailure: DecodingFailure) =>
      implicit val builder = new FlatBufferBuilder(1024)
      val error =
        decodingFailure match {
          case EmptyPayload  => ErrorFactory.createReceivedEmptyPayloadError()
          case DataCorrupted => ErrorFactory.createReceivedCorruptedDataError()
          case GenericDecodingFailure(th) =>
            log.error("Unrecognized error occurred in binary protocol", th)
            ErrorFactory.createServiceError()
        }
      val outMsg = OutboundMessageFactory.create(
        UUID.randomUUID(),
        None,
        OutboundPayload.ERROR,
        error
      )
      builder.finish(outMsg)
      outboundChannel ! builder.dataBuffer()
  }

  private def initialized(
    outboundChannel: ActorRef,
    clientId: UUID
  ): Receive = {
    case ConnectionClosed =>
      context.stop(self)

    case msg =>
      log.info(s"$msg received by binary protocol")
  }

}
