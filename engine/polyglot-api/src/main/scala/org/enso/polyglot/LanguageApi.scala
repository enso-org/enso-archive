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
      value = classOf[LanguageApi.CreateContextRequest],
      name  = "createContextRequest"
    ),
    new JsonSubTypes.Type(
      value = classOf[LanguageApi.CreateContextResponse],
      name  = "createContextResponse"
    )
  )
)
sealed trait LanguageApi

object LanguageApi {
  type ContextId = UUID

  case class CreateContextRequest(id: ContextId)  extends LanguageApi
  case class CreateContextResponse(id: ContextId) extends LanguageApi

  private lazy val factory = new CBORFactory()
  private lazy val mapper = {
    val mapper = new ObjectMapper(factory) with ScalaObjectMapper
    mapper.registerModule(DefaultScalaModule)
  }

  def serialize(serverApi: LanguageApi): ByteBuffer =
    ByteBuffer.wrap(mapper.writeValueAsBytes(serverApi))

  def deserialize(bytes: ByteBuffer): Option[LanguageApi] =
    Try(mapper.readValue(bytes.array(), classOf[LanguageApi])).toOption
}
