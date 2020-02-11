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

  case class MyMethod(foo: Int, bar: String) extends MyProtocol

  case class MyMethodResponse(baz: Int) extends MyProtocol

  object MyProtocol {
    import io.circe.generic.auto._
    import io.circe.syntax._
    import cats.syntax.functor._

    val encoder: Encoder[MyProtocol] = Encoder.instance {
      case m: MyMethod         => m.asJson
      case m: MyMethodResponse => m.asJson
    }

    val protocol =
      Protocol(
        Set(MyMethodTag),
        Map(MyMethodTag -> implicitly[Decoder[MyMethod]].widen[MyProtocol]),
        Map(
          MyMethodTag -> implicitly[Decoder[MyMethodResponse]].widen[MyProtocol]
        ),
        Map(),
        encoder
      )
  }

  case object MyMethodTag extends MethodTag("MyMethod")

  "foo" must {
    "work" in {
      val out     = TestProbe()
      val handler = TestProbe()
      val x = system.actorOf(
        Props(new MessageHandler(MyProtocol.protocol, handler.ref))
      )
      x ! Connected(out.ref)
      x ! IncomingMessage("""
                            |{ "jsonrpc": "2.0",
                            |  "method": "MyMethod",
                            |  "params": {"foo": 30, "bar": "bar"},
                            |  "id": "1234"
                            |}
                            |""".stripMargin)
      handler.expectMsg(
        Request(MyMethodTag, "1234", MyMethod(30, "bar"))
      )
      handler.reply(
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
    maybeJson.toOption.get shouldEqual expectedJson
  }
}
