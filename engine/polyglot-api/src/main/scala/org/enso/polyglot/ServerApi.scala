package org.enso.polyglot

import java.nio.ByteBuffer
import java.util.UUID

import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import com.fasterxml.jackson.module.scala.{
  DefaultScalaModule,
  ScalaObjectMapper
}

import scala.util.Try

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
  Array(
    new JsonSubTypes.Type(
      value = classOf[CreateContext],
      name  = "createContext"
    ),
    new JsonSubTypes.Type(
      value = classOf[DestroyContext],
      name  = "destroyContext"
    )
  )
)
sealed trait ServerApi

case class CreateContext(id: UUID)  extends ServerApi
case class DestroyContext(id: UUID) extends ServerApi

object ServerApiSerialization {
  private lazy val factory = new CBORFactory()
  private lazy val mapper = {
    val mapper = new ObjectMapper(factory) with ScalaObjectMapper
    mapper.registerModule(DefaultScalaModule)
  }

  def serialize(serverApi: ServerApi): ByteBuffer =
    ByteBuffer.wrap(mapper.writeValueAsBytes(serverApi))

  def deserialize(bytes: ByteBuffer): Option[ServerApi] =
    Try(mapper.readValue(bytes.array(), classOf[ServerApi])).toOption

}
