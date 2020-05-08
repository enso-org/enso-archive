package org.enso.languageserver.websocket.data

import java.io.File
import java.nio.ByteBuffer
import java.util.UUID

import com.google.flatbuffers.FlatBufferBuilder
import org.enso.languageserver.protocol.data.envelope.{
  InboundPayload,
  OutboundMessage,
  OutboundPayload
}
import org.enso.languageserver.websocket.data.factory.{
  InboundMessageFactory,
  PathFactory,
  WriteFileCommandFactory
}

import scala.annotation.unused
import scala.io.Source

class BinaryFileManipulationTest extends BaseBinaryServerTest {

  implicit private val decoder = OutboundMessageDecoder

  "A WriteFileCommand" must {

    "persist file content" in {
      val requestId = UUID.randomUUID()
      val filename  = "foo.bin"
      @unused
      val fooFile  = new File(testContentRoot.toFile, filename)
      val contents = Array[Byte](65, 66, 67) //ABC
      val client   = newWsClient()
      client.send(createSessionInitCmd())
      client.expectFrame()
      val writeFileCommand = createWriteFileCmdPacket(
        requestId,
        filename,
        testContentRootId,
        contents
      )
      client.send(writeFileCommand)
      val Right(msg) = client.receiveMessage[OutboundMessage]()
      //then
      msg.payloadType() shouldBe OutboundPayload.SUCCESS
      msg
        .correlationId()
        .leastSigBits() shouldBe requestId.getLeastSignificantBits
      msg
        .correlationId()
        .mostSigBits() shouldBe requestId.getMostSignificantBits
      Source.fromFile(fooFile).mkString shouldBe "ABC"
    }

  }

  def createWriteFileCmdPacket(
    requestId: UUID,
    pathSegment: String,
    rootId: UUID,
    contents: Array[Byte]
  ): ByteBuffer = {
    implicit val builder = new FlatBufferBuilder(1024)

    val path = PathFactory.create(rootId, Seq(pathSegment))

    val cmd = WriteFileCommandFactory.create(path, contents)

    val inMsg = InboundMessageFactory.create(
      requestId,
      None,
      InboundPayload.WRITE_FILE_CMD,
      cmd
    )
    builder.finish(inMsg)
    builder.dataBuffer()
  }

}
