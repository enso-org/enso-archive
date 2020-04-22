package org.enso.languageserver.protocol.data

import java.nio.ByteBuffer
import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, Stash}
import akka.http.scaladsl.model.RemoteAddress
import com.google.flatbuffers.FlatBufferBuilder
import org.enso.languageserver.http.server.WebSocketControlProtocol.{
  ConnectionClosed,
  ConnectionFailed,
  OutboundStreamEstablished
}
import org.enso.languageserver.protocol.data.envelope.InboundPayload.SESSION_INIT
import org.enso.languageserver.protocol.data.envelope.{
  InboundMessage,
  OutboundPayload
}
import org.enso.languageserver.protocol.data.factory.{
  ErrorFactory,
  OutboundMessageFactory,
  SessionInitResponseFactory,
  VisualisationUpdateFactory
}
import org.enso.languageserver.protocol.data.session.SessionInit
import org.enso.languageserver.protocol.data.util.EnsoUUID
import org.enso.languageserver.runtime.VisualisationProtocol.VisualisationUpdate
import org.enso.languageserver.util.UnhandledLogging
import org.enso.languageserver.util.binary.DecodingFailure
import org.enso.languageserver.util.binary.DecodingFailure.{
  DataCorrupted,
  EmptyPayload,
  GenericDecodingFailure
}

import scala.annotation.unused

/**
  * An actor handling data communications between a single client and the
  * language server.
  *
  * @param maybeIp a client ip address
  */
class BinaryConnectionController(maybeIp: Option[RemoteAddress.IP])
    extends Actor
    with Stash
    with ActorLogging
    with UnhandledLogging {

  override def receive: Receive =
    connectionEndHandler orElse connectionNotEstablished

  private def connectionNotEstablished: Receive = {
    case OutboundStreamEstablished(outboundChannel) =>
      log.info(s"Connection established [$maybeIp]")
      unstashAll()
      context.become(
        connected(outboundChannel) orElse connectionEndHandler orElse decodingFailureHandler(
          outboundChannel
        )
      )

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

      val responsePacket = createSessionInitResponsePacket(msg.requestId())
      outboundChannel ! responsePacket
      context.become(
        connectionEndHandler orElse initialized(outboundChannel, clientID) orElse decodingFailureHandler(
          outboundChannel
        )
      )

  }

  private def initialized(
    outboundChannel: ActorRef,
    @unused clientId: UUID
  ): Receive = {
    case update: VisualisationUpdate =>
      val updatePacket = convertVisualisationUpdateToOutPacket(update)
      outboundChannel ! updatePacket
  }

  private def connectionEndHandler: Receive = {
    case ConnectionClosed =>
      context.stop(self)

    case ConnectionFailed(th) =>
      log.error(
        s"An error occurred during processing web socket connection [$maybeIp]",
        th
      )
      context.stop(self)
  }

  private def decodingFailureHandler(outboundChannel: ActorRef): Receive = {
    case Left(decodingFailure: DecodingFailure) =>
      val packet = convertDecodingFailureToOutPacket(decodingFailure)
      outboundChannel ! packet
  }

  private def convertDecodingFailureToOutPacket(
    decodingFailure: DecodingFailure
  ): ByteBuffer = {
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
    builder.dataBuffer()
  }

  private def convertVisualisationUpdateToOutPacket(
    update: VisualisationUpdate
  ): ByteBuffer = {
    implicit val builder = new FlatBufferBuilder(1024)
    val event            = VisualisationUpdateFactory.create(update)
    val msg = OutboundMessageFactory.create(
      UUID.randomUUID(),
      None,
      OutboundPayload.VISUALISATION_UPDATE,
      event
    )

    builder.finish(msg)
    val updatePacket = builder.dataBuffer()
    updatePacket
  }

  private def createSessionInitResponsePacket(
    requestId: EnsoUUID
  ): ByteBuffer = {
    implicit val builder = new FlatBufferBuilder(1024)
    val outMsg = OutboundMessageFactory.create(
      UUID.randomUUID(),
      Some(requestId),
      OutboundPayload.SESSION_INIT_RESPONSE,
      SessionInitResponseFactory.create()
    )
    builder.finish(outMsg)
    val responsePacket = builder.dataBuffer()
    responsePacket
  }

}