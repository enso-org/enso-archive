package org.enso.interpeter.instrument

import java.io.File
import java.nio.ByteBuffer
import java.util.{Optional, UUID}
import java.util.function.Consumer

import com.oracle.truffle.api.TruffleContext
import org.enso.interpreter.instrument.IdExecutionInstrument.{
  ExpressionCall,
  ExpressionValue
}
import org.enso.interpreter.node.callable.FunctionCallInstrumentationNode.FunctionCall
import org.enso.interpreter.runtime.Module
import org.enso.interpreter.service.ExecutionService
import org.enso.polyglot.runtime.Runtime.Api
import org.graalvm.polyglot.io.MessageEndpoint

import scala.jdk.javaapi.OptionConverters
import scala.jdk.CollectionConverters._

/**
  * A message endpoint implementation used by the
  * [[org.enso.interpreter.instrument.RuntimeServerInstrument]].
  * @param handler
  */
class Endpoint(handler: Handler) extends MessageEndpoint {
  var client: MessageEndpoint = _

  /**
    * Sets the client end of the connection, after it has been established.
    *
    * @param ep the client endpoint.
    */
  def setClient(ep: MessageEndpoint): Unit = client = ep

  /**
    * Sends a response to the connected client.
    *
    * @param msg the message to send.
    */
  def sendToClient(msg: Api.Response): Unit =
    client.sendBinary(Api.serialize(msg))

  override def sendText(text: String): Unit = {}

  override def sendBinary(data: ByteBuffer): Unit =
    Api.deserializeRequest(data).foreach(handler.onMessage)

  override def sendPing(data: ByteBuffer): Unit = client.sendPong(data)

  override def sendPong(data: ByteBuffer): Unit = {}

  override def sendClose(): Unit = {}
}

/**
  * A message handler, dispatching behaviors based on messages received
  * from an instance of [[Endpoint]].
  */
final class Handler {
  val endpoint       = new Endpoint(this)
  val contextManager = new ExecutionContextManager

  var executionService: ExecutionService = _
  var truffleContext: TruffleContext     = _

  /**
    * Initializes the handler with relevant Truffle objects, allowing it to
    * perform code execution.
    *
    * @param service the language execution service instance.
    * @param context the current Truffle context.
    */
  def initializeExecutionService(
    service: ExecutionService,
    context: TruffleContext
  ): Unit = {
    executionService = service
    truffleContext   = context
    endpoint.sendToClient(Api.Response(Api.InitializedNotification()))
  }

  sealed private trait ExecutionItem

  private object ExecutionItem {
    case class Method(
      file: File,
      constructor: String,
      function: String
    ) extends ExecutionItem

    case class CallData(callData: FunctionCall) extends ExecutionItem
  }

  private def onExpressionValueComputed(
    contextId: Api.ContextId,
    value: ExpressionValue
  ): Unit = {
    endpoint.sendToClient(
      Api.Response(
        Api.ExpressionValuesComputed(
          contextId,
          Vector(
            Api.ExpressionValueUpdate(
              value.getExpressionId,
              OptionConverters.toScala(value.getType),
              Some(value.getValue.toString),
              None
            )
          )
        )
      )
    )
    val maybeVisualisation =
      contextManager.findVisualisation(contextId, value.getExpressionId)
    maybeVisualisation foreach { visualisation =>
      withContext {
        val fun = executionService.evalExpr(
          visualisation.module,
          visualisation.expression
        )
        val function = fun
          .asInstanceOf[org.enso.interpreter.runtime.callable.function.Function]
        val result = executionService.callFn(function, value.getValue)
        println(s"result: $result")
        val data = result match {
          case txt: String      => txt.getBytes("UTF-8")
          case arr: Array[Byte] => arr
          case other =>
            throw new RuntimeException(
              s"Cannot encode ${other.getClass} to byte array"
            )
        }

        endpoint.sendToClient(
          Api.Response(
            Api.VisualisationUpdate(
              Api.VisualisationContext(
                visualisation.id,
                contextId,
                value.getExpressionId
              ),
              data
            )
          )
        )
      }
    }
  }

  @scala.annotation.tailrec
  private def execute(
    executionItem: ExecutionItem,
    callStack: List[UUID],
    valueCallback: Consumer[ExpressionValue]
  ): Unit = {
    var enterables: Map[UUID, FunctionCall] = Map()
    val valsCallback: Consumer[ExpressionValue] =
      if (callStack.isEmpty) valueCallback else _ => ()
    val callablesCallback: Consumer[ExpressionCall] = fun =>
      enterables += fun.getExpressionId -> fun.getCall
    executionItem match {
      case ExecutionItem.Method(file, cons, function) =>
        executionService.execute(
          file,
          cons,
          function,
          valsCallback,
          callablesCallback
        )
      case ExecutionItem.CallData(callData) =>
        executionService.execute(callData, valsCallback, callablesCallback)
    }

    callStack match {
      case Nil => ()
      case item :: tail =>
        enterables.get(item) match {
          case Some(call) =>
            execute(ExecutionItem.CallData(call), tail, valueCallback)
          case None =>
            ()
        }
    }
  }

  private def execute(
    contextId: Api.ContextId,
    stack: List[Api.StackItem]
  ): Unit = {
    def unwind(
      stack: List[Api.StackItem],
      explicitCalls: List[Api.StackItem.ExplicitCall],
      localCalls: List[UUID]
    ): (List[Api.StackItem.ExplicitCall], List[UUID]) =
      stack match {
        case Nil =>
          (explicitCalls, localCalls)
        case List(call: Api.StackItem.ExplicitCall) =>
          (List(call), localCalls)
        case Api.StackItem.LocalCall(id) :: xs =>
          unwind(xs, explicitCalls, id :: localCalls)
      }
    val (explicitCalls, localCalls) = unwind(stack, Nil, Nil)
    explicitCalls.headOption.foreach { item =>
      execute(
        toExecutionItem(item),
        localCalls,
        onExpressionValueComputed(contextId, _)
      )
    }
  }

  private def executeAll(): Unit =
    contextManager.getAll
      .filter(kv => kv._2.nonEmpty)
      .mapValues(_.toList)
      .foreach(Function.tupled(execute))

  private def toExecutionItem(
    call: Api.StackItem.ExplicitCall
  ): ExecutionItem =
    ExecutionItem.Method(
      call.methodPointer.file,
      call.methodPointer.definedOnType,
      call.methodPointer.name
    )

  private def withContext(action: => Unit): Unit = {
    val token = truffleContext.enter()
    try {
      action
    } finally {
      truffleContext.leave(token)
    }
  }

  /**
    * Handles a message received from the client.
    *
    * @param msg the message to handle.
    */
  def onMessage(msg: Api.Request): Unit = {
    val requestId = msg.requestId
    msg.payload match {
      case Api.CreateContextRequest(contextId) =>
        contextManager.create(contextId)
        endpoint.sendToClient(
          Api.Response(requestId, Api.CreateContextResponse(contextId))
        )

      case Api.PushContextRequest(contextId, item) => {
        if (contextManager.get(contextId).isDefined) {
          val stack = contextManager.getStack(contextId)
          val payload = item match {
            case call: Api.StackItem.ExplicitCall if stack.isEmpty =>
              contextManager.push(contextId, item)
              withContext(execute(contextId, List(call)))
              Api.PushContextResponse(contextId)
            case _: Api.StackItem.LocalCall if stack.nonEmpty =>
              contextManager.push(contextId, item)
              withContext(execute(contextId, stack.toList))
              Api.PushContextResponse(contextId)
            case _ =>
              Api.InvalidStackItemError(contextId)
          }
          endpoint.sendToClient(Api.Response(requestId, payload))
        } else {
          endpoint.sendToClient(
            Api.Response(requestId, Api.ContextNotExistError(contextId))
          )
        }
      }

      case Api.PopContextRequest(contextId) =>
        if (contextManager.get(contextId).isDefined) {
          val payload = contextManager.pop(contextId) match {
            case Some(_: Api.StackItem.ExplicitCall) =>
              Api.PopContextResponse(contextId)
            case Some(_: Api.StackItem.LocalCall) =>
              withContext(
                execute(contextId, contextManager.getStack(contextId).toList)
              )
              Api.PopContextResponse(contextId)
            case None =>
              Api.EmptyStackError(contextId)
          }
          endpoint.sendToClient(Api.Response(requestId, payload))
        } else {
          endpoint.sendToClient(
            Api.Response(requestId, Api.ContextNotExistError(contextId))
          )
        }

      case Api.DestroyContextRequest(contextId) =>
        if (contextManager.get(contextId).isDefined) {
          contextManager.destroy(contextId)
          endpoint.sendToClient(
            Api.Response(requestId, Api.DestroyContextResponse(contextId))
          )
        } else {
          endpoint.sendToClient(
            Api.Response(requestId, Api.ContextNotExistError(contextId))
          )
        }

      case Api.RecomputeContextRequest(contextId, _) =>
        if (contextManager.get(contextId).isDefined) {
          val stack = contextManager.getStack(contextId)
          val payload = if (stack.isEmpty) {
            Api.EmptyStackError(contextId)
          } else {
            withContext(execute(contextId, stack.toList))
            Api.RecomputeContextResponse(contextId)
          }
          endpoint.sendToClient(Api.Response(requestId, payload))
        } else {
          endpoint.sendToClient(
            Api.Response(requestId, Api.ContextNotExistError(contextId))
          )
        }

      case Api.OpenFileNotification(path, contents) =>
        executionService.setModuleSources(path, contents)

      case Api.CloseFileNotification(path) =>
        executionService.resetModuleSources(path)

      case Api.EditFileNotification(path, edits) =>
        executionService.modifyModuleSources(path, edits.asJava)
        withContext(executeAll())

      case Api.AttachVisualisation(visualisationId, expressionId, config) =>
        println("received")
        if (contextManager.contains(config.executionContextId)) {
          println("context")
          var module = Optional.empty[Module]()
          withContext {
            module = executionService.findModule(config.visualisationModule)
          }
          if (module.isPresent) {
            println("module")
            val visualisation = Visualisation(
              visualisationId,
              expressionId,
              module.get(),
              config.expression
            )
            contextManager.attachVisualisation(
              config.executionContextId,
              visualisation
            )
            endpoint.sendToClient(
              Api.Response(requestId, Api.VisualisationAttached())
            )
          } else {
            println("no module")
            endpoint.sendToClient(
              Api.Response(
                requestId,
                Api.ModuleNotFound(config.visualisationModule)
              )
            )
          }
        } else {
          endpoint.sendToClient(
            Api.Response(
              requestId,
              Api.ContextNotExistError(config.executionContextId)
            )
          )
        }
    }
  }
}
