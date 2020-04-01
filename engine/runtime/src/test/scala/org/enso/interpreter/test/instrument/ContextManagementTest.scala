package org.enso.interpreter.test.instrument

import java.nio.ByteBuffer
import java.util.UUID

import org.enso.polyglot.runtime.Runtime.Api
import org.enso.polyglot.{LanguageInfo, RuntimeServerInfo}
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.io.MessageEndpoint
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ContextManagementTest
    extends AnyFlatSpec
    with Matchers
    with BeforeAndAfterEach {

  var context: Context          = _
  var messageQueue: List[Api]   = _
  var endPoint: MessageEndpoint = _

  override protected def beforeEach(): Unit = {
    messageQueue = List()
    context = Context
      .newBuilder(LanguageInfo.ID)
      .allowExperimentalOptions(true)
      .option(RuntimeServerInfo.ENABLE_OPTION, "true")
      .serverTransport { (uri, peer) =>
        if (uri.toString == RuntimeServerInfo.URI) {
          endPoint = peer
          new MessageEndpoint {
            override def sendText(text: String): Unit = {}

            override def sendBinary(data: ByteBuffer): Unit =
              messageQueue ++= Api.deserialize(data)

            override def sendPing(data: ByteBuffer): Unit = {}

            override def sendPong(data: ByteBuffer): Unit = {}

            override def sendClose(): Unit = {}
          }
        } else null
      }
      .build()
  }

  def send(msg: Api): Unit = endPoint.sendBinary(Api.serialize(msg))
  def receive: Option[Api] = {
    val msg = messageQueue.headOption
    messageQueue = messageQueue.drop(1)
    msg
  }
  def nextId: UUID = UUID.randomUUID()

  "Runtime server" should "allow context creation and deletion" in {
    val requestId1 = nextId
    val requestId2 = nextId
    val contextId = nextId
    send(Api.CreateContextRequest(requestId1, contextId))
    receive shouldEqual Some(Api.CreateContextResponse(requestId1, contextId))
    send(Api.DestroyContextRequest(requestId2, contextId))
    receive shouldEqual Some(Api.DestroyContextResponse(requestId2, contextId, None))
  }

  "Runtime server" should "fail destroying a context if it does not exist" in {
    val requestId1 = nextId
    val contextId1 = nextId
    val requestId2 = nextId
    val contextId2 = nextId
    send(Api.CreateContextRequest(requestId1, contextId1))
    receive shouldEqual Some(Api.CreateContextResponse(requestId1, contextId1))
    send(Api.DestroyContextRequest(requestId2, contextId2))
    receive shouldEqual Some(
      Api.DestroyContextResponse(requestId2, contextId2, Some(Api.ContextDoesNotExistError()))
    )
  }
}
