package org.enso.jsonrpcserver
import akka.actor.{ActorSystem, Props}
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

  sealed trait MyProtocol

  case object MyMethodTag                    extends MethodTag[MyProtocol]("MyMethod")
  case class MyMethod(foo: Int, bar: String) extends MyProtocol
  case class MyMethodResponse(baz: Int)      extends MyProtocol

  case object NotificationTag
      extends MethodTag[MyProtocol]("NotificationMethod")
  case class MyNotification(spam: String) extends MyProtocol

  object MyProtocol {
    import io.circe.generic.auto._
    import io.circe.syntax._
    import cats.syntax.functor._

    val encoder: Encoder[MyProtocol] = Encoder.instance {
      case m: MyMethod         => m.asJson
      case m: MyMethodResponse => m.asJson
      case m: MyNotification   => m.asJson
    }

    val protocol: Protocol[MyProtocol] =
      Protocol(
        Set(MyMethodTag, NotificationTag),
        Map(MyMethodTag -> implicitly[Decoder[MyMethod]].widen),
        Map(
          MyMethodTag -> implicitly[Decoder[MyMethodResponse]].widen
        ),
        Map(NotificationTag -> implicitly[Decoder[MyNotification]].widen),
        encoder
      )
  }

  "Message handler" must {
    "be able to issue notifications" in {
      val out        = TestProbe()
      val controller = TestProbe()
      val handler = system.actorOf(
        Props(new MessageHandler(MyProtocol.protocol, controller.ref))
      )

      handler ! Connected(out.ref)
      handler ! Notification(NotificationTag, MyNotification("test"))

      expectJson(out, json"""
          { "jsonrpc": "2.0",
            "method": "NotificationMethod",
            "params": { "spam": "test" }
          }""")
    }

    "be able to reply to requests" in {
      val out        = TestProbe()
      val controller = TestProbe()
      val handler = system.actorOf(
        Props(new MessageHandler(MyProtocol.protocol, controller.ref))
      )
      handler ! Connected(out.ref)
      handler ! IncomingMessage("""
                                  |{ "jsonrpc": "2.0",
                                  |  "method": "MyMethod",
                                  |  "params": {"foo": 30, "bar": "bar"},
                                  |  "id": "1234"
                                  |}
                                  |""".stripMargin)
      controller.expectMsg(
        Request(MyMethodTag, "1234", MyMethod(30, "bar"))
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
          }
        """
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
