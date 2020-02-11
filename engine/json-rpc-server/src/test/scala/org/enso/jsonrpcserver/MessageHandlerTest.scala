package org.enso.jsonrpcserver
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import io.circe.{Decoder, Encoder, Json}
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers, WordSpecLike}

import scala.concurrent.duration._
import io.circe.parser._
import io.circe.literal._
import org.enso.jsonrpcserver.MessageHandler.{
  Connected,
  IncomingMessage,
  OutgoingMessage
}

class MessageHandlerTest
    extends TestKit(ActorSystem("TestSystem"))
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll {

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  case object MyMethod extends Method("MyMethod")
  case class MyMethodParams(foo: Int, bar: String)
      extends ParamsOf[MyMethod.type]
  case class MyMethodResponse(baz: Int) extends ResultOf[MyMethod.type]

  case object MyNotification extends Method("NotificationMethod")
  case class MyNotificationParams(spam: String)
      extends ParamsOf[MyNotification.type]

  object MyProtocol {
    import io.circe.generic.auto._
    import io.circe.syntax._
    import cats.syntax.functor._

    val encoder: Encoder[DataOf[Method]] = Encoder.instance {
      case m: MyMethodParams       => m.asJson
      case m: MyMethodResponse     => m.asJson
      case m: MyNotificationParams => m.asJson
    }

    val protocol: Protocol =
      Protocol(
        Set(MyMethod, MyNotification),
        Map(
          MyNotification -> implicitly[Decoder[MyNotificationParams]].widen,
          MyMethod       -> implicitly[Decoder[MyMethodParams]].widen
        ),
        Map(
          MyMethod -> implicitly[Decoder[MyMethodResponse]].widen
        ),
        encoder
      )
  }

  var out: TestProbe        = _
  var controller: TestProbe = _
  var handler: ActorRef     = _

  override def beforeAll(): Unit = {
    out        = TestProbe()
    controller = TestProbe()
    handler = system.actorOf(
      Props(new MessageHandler(MyProtocol.protocol, controller.ref))
    )
    handler ! Connected(out.ref)
  }

  "Message handler" must {

    "issue notifications" in {
      handler ! Notification(
        MyNotification,
        MyNotificationParams("test")
      )

      expectJson(out, json"""
          { "jsonrpc": "2.0",
            "method": "NotificationMethod",
            "params": { "spam": "test" }
          }""")
    }

    "receive notifications" in {
      handler ! IncomingMessage("""
                                  |{ "jsonrpc": "2.0",
                                  |  "method": "NotificationMethod",
                                  |  "params": { "spam": "hello" }
                                  |}
                                  |""".stripMargin)

      controller.expectMsg(
        Notification(MyNotification, MyNotificationParams("hello"))
      )
    }

    "reply to requests" in {
      handler ! IncomingMessage("""
                                  |{ "jsonrpc": "2.0",
                                  |  "method": "MyMethod",
                                  |  "params": {"foo": 30, "bar": "bar"},
                                  |  "id": "1234"
                                  |}
                                  |""".stripMargin)
      controller.expectMsg(
        Request(MyMethod, "1234", MyMethodParams(30, "bar"))
      )
      controller.reply(
        ResponseResult(Some("1234"), MyMethodResponse(123))
      )

      expectJson(
        out,
        json"""
          { "jsonrpc": "2.0",
            "id": "1234",
            "result": {"baz": 123}
          }"""
      )
    }

    "reply with an error to malformed messages" in {
      handler ! IncomingMessage("Is this a JSON RPC message...?")
      expectJson(
        out,
        json"""
          { "jsonrpc": "2.0",
            "id": null,
            "error": { "code": -32700,
                       "message": "Parse error"
                     }
          }"""
      )
    }

    "reply with an error to unrecognized messages" in {
      handler ! IncomingMessage("""
                                  |{ "jsonrpc": "2.0",
                                  |  "method": "MyMethodZZZZZ",
                                  |  "params": {"foo": 30, "bar": "bar"},
                                  |  "id": "1234"
                                  |}
                                  |""".stripMargin)

      expectJson(
        out,
        json"""
          { "jsonrpc": "2.0",
            "id": "1234",
            "error": { "code": -32601,
                       "message": "Method not found"
                     }
          }"""
      )
    }

    "reply with an error to messages with wrong params" in {
      handler ! IncomingMessage("""
                                  |{ "jsonrpc": "2.0",
                                  |  "method": "MyMethod",
                                  |  "params": {"foop": 30, "barp": "bar"},
                                  |  "id": "1234"
                                  |}
                                  |""".stripMargin)

      expectJson(
        out,
        json"""
          { "jsonrpc": "2.0",
            "id": "1234",
            "error": { "code": -32602,
                       "message": "Invalid params"
                     }
          }"""
      )
    }
  }

  def expectJson(probe: TestProbe, expectedJson: Json): Unit = {
    val msg = probe.receiveOne(1.seconds)
    msg shouldBe an[OutgoingMessage]
    val contents  = msg.asInstanceOf[OutgoingMessage].message
    val maybeJson = parse(contents)
    maybeJson shouldBe 'right
    maybeJson.right.get shouldEqual expectedJson
  }
}
