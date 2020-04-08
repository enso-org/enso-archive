package org.enso.polyglot.runtime

import java.nio.ByteBuffer
import java.nio.file.Path
import java.util.UUID

import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import com.fasterxml.jackson.module.scala.{
  DefaultScalaModule,
  ScalaObjectMapper
}

import scala.util.Try

object Runtime {

  /**
    * A common supertype for all Runtime API methods.
    */
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(
    Array(
      new JsonSubTypes.Type(
        value = classOf[Api.CreateContextRequest],
        name  = "createContextRequest"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.CreateContextResponse],
        name  = "createContextResponse"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.DestroyContextRequest],
        name  = "destroyContextRequest"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.DestroyContextResponse],
        name  = "destroyContextResponse"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.PushContextRequest],
        name  = "pushContextRequest"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.PushContextResponse],
        name  = "pushContextResponse"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.PopContextRequest],
        name  = "popContextRequest"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.PopContextResponse],
        name  = "popContextResponse"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.ContextNotExistError],
        name  = "contextNotExistError"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.EmptyStackError],
        name  = "emptyStackError"
      )
    )
  )
  sealed trait Api
  sealed trait ApiRequest extends Api
  sealed trait ApiResponse extends Api

  object Api {

    type ContextId = UUID
    type ExpressionId = UUID
    type RequestId = UUID

    /**
      * Indicates error response.
      */
    sealed trait Error extends ApiResponse

    /**
      * A representation of a pointer to a method definition.
      */
    case class MethodPointer(file: Path, definedOnType: String, name: String)

    /**
      * A representation of an executable position in code.
      */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes(
      Array(
        new JsonSubTypes.Type(
          value = classOf[StackItem.ExplicitCall],
          name  = "explicitCall"
        ),
        new JsonSubTypes.Type(
          value = classOf[StackItem.LocalCall],
          name  = "localCall"
        )
      )
    )
    sealed trait StackItem

    object StackItem {

      /**
        * A call performed at the top of the stack, to initialize the context.
        */
      case class ExplicitCall(
        methodPointer: MethodPointer,
        thisArgumentExpression: Option[String],
        positionalArgumentsExpressions: Vector[String]
      ) extends StackItem

      /**
        * A call corresponding to "entering a function call".
        */
      case class LocalCall(expressionId: ExpressionId) extends StackItem
    }

    /**
      * Envelope for an Api request.
      *
      * @param requestId request identifier
      * @param payload request
      */
    case class Request(requestId: RequestId, payload: ApiRequest)

    /**
      * Envelope for an Api response.
      *
      * @param correlationId request that initiated the response
      * @param payload response
      */
    case class Response(correlationId: RequestId, payload: ApiResponse)

    /**
      * A Request sent from the client to the runtime server, to create a new
      * execution context with a given id.
      *
      * @param contextId the newly created context's id.
      */
    case class CreateContextRequest(contextId: ContextId) extends ApiRequest

    /**
      * A response sent from the server upon handling the [[CreateContextRequest]]
      *
      * @param contextId the newly created context's id.
      */
    case class CreateContextResponse(contextId: ContextId) extends ApiResponse

    /**
      * A Request sent from the client to the runtime server, to destroy an
      * execution context with a given id.
      *
      * @param contextId the destroyed context's id.
      */
    case class DestroyContextRequest(contextId: ContextId) extends ApiRequest

    /**
      * A success response sent from the server upon handling the
      * [[DestroyContextRequest]]
      *
      * @param contextId the destroyed context's id
      */
    case class DestroyContextResponse(contextId: ContextId) extends ApiResponse

    /**
      * A Request sent from the client to the runtime server, to move
      * the execution context to a new location deeper down the stack.
      *
      * @param contextId the context's id.
      * @param stackItem an item that should be pushed on the stack.
      */
    case class PushContextRequest(contextId: ContextId, stackItem: StackItem)
        extends ApiRequest

    /**
      * A response sent from the server upon handling the [[PushContextRequest]]
      *
      * @param contextId the context's id.
      */
    case class PushContextResponse(contextId: ContextId) extends ApiResponse

    /**
      * A Request sent from the client to the runtime server, to move
      * the execution context up the stack.
      *
      * @param contextId the context's id.
      */
    case class PopContextRequest(contextId: ContextId) extends ApiRequest

    /**
      * A response sent from the server upon handling the [[PopContextRequest]]
      *
      * @param contextId the context's id.
      */
    case class PopContextResponse(contextId: ContextId) extends ApiResponse

    /**
      * An error response signifying a non-existent context.
      *
      * @param contextId the context's id
      */
    case class ContextNotExistError(contextId: ContextId) extends Error

    /**
      * An error response signifying that stack is empty.
      *
      * @param contextId the context's id
      */
    case class EmptyStackError(contextId: ContextId) extends Error

    private lazy val mapper = {
      val factory = new CBORFactory()
      val mapper = new ObjectMapper(factory) with ScalaObjectMapper
      mapper.registerModule(DefaultScalaModule)
    }

    /**
      * Serializes a Request into a byte buffer.
      *
      * @param message the message to serialize.
      * @return the serialized version of the message.
      */
    def serialize(message: Request): ByteBuffer =
      ByteBuffer.wrap(mapper.writeValueAsBytes(message))

    /**
      * Serializes a Response into a byte buffer.
      *
      * @param message the message to serialize.
      * @return the serialized version of the message.
      */
    def serialize(message: Response): ByteBuffer =
      ByteBuffer.wrap(mapper.writeValueAsBytes(message))

    /**
      * Deserializes a byte buffer into a Request message.
      *
      * @param bytes the buffer to deserialize
      * @return the deserialized message, if the byte buffer can be deserialized.
      */
    def deserializeRequest(bytes: ByteBuffer): Option[Request] =
      Try(mapper.readValue(bytes.array(), classOf[Request])).toOption

    /**
      * Deserializes a byte buffer into a Response message.
      *
      * @param bytes the buffer to deserialize
      * @return the deserialized message, if the byte buffer can be deserialized.
      */
    def deserializeResponse(bytes: ByteBuffer): Option[Response] =
      Try(mapper.readValue(bytes.array(), classOf[Response])).toOption
  }

}
