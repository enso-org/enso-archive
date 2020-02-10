package org.enso.jsonrpcserver
import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import io.circe.Decoder
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers, WordSpecLike}
import io.circe.literal._
import org.enso.jsonrpcserver.MessageHandler.{Connected, IncomingMessage}

class MessageHandlerTest
    extends TestKit(ActorSystem("TestSystem"))
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll {

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  case class MyMethod(foo: Int, bar: String)

  case class MyMethodResponse(baz: Int)

  case object MyMethodTag extends MethodTag("MyMethod")

  val MyMethSer = RequestSerializer[MyMethod, MyMethodResponse](
    Serializer(
      io.circe.generic.semiauto.deriveDecoder,
      io.circe.generic.semiauto.deriveEncoder
    ),
    Serializer(
      io.circe.generic.semiauto.deriveDecoder,
      io.circe.generic.semiauto.deriveEncoder
    )
  )

  val protocol = Protocol(Set(MyMethodTag), Map(MyMethodTag -> MyMethSer), Map())

  "foo" must {
    "work" in {
      val out     = TestProbe()
      val handler = TestProbe()
      val x = system.actorOf(
        Props(new MessageHandler(protocol, handler.ref))
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
    }
  }
}
