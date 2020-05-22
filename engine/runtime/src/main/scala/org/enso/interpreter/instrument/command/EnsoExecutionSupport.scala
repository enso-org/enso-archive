package org.enso.interpreter.instrument.command

import java.io.File
import java.util.UUID
import java.util.function.Consumer
import java.util.logging.Level

import org.enso.interpreter.instrument.IdExecutionInstrument.{
  ExpressionCall,
  ExpressionValue
}
import org.enso.interpreter.instrument.RuntimeContext
import org.enso.interpreter.instrument.command.EnsoExecutionSupport.ExecutionItem
import org.enso.interpreter.node.callable.FunctionCallInstrumentationNode.FunctionCall
import org.enso.polyglot.runtime.Runtime.Api
import cats.implicits._

import scala.annotation.unused

trait EnsoExecutionSupport {

  def withContext[A](action: => A)(implicit ctx: RuntimeContext): A = {
    val token = ctx.truffleContext.enter()
    try {
      action
    } finally {
      ctx.truffleContext.leave(token)
    }
  }

  @scala.annotation.tailrec
  final def runEnso(
    executionItem: ExecutionItem,
    callStack: List[UUID],
    valueCallback: Consumer[ExpressionValue]
  )(implicit ctx: RuntimeContext): Unit = {
    var enterables: Map[UUID, FunctionCall] = Map()
    val valsCallback: Consumer[ExpressionValue] =
      if (callStack.isEmpty) valueCallback else _ => ()
    val callablesCallback: Consumer[ExpressionCall] = fun =>
      enterables += fun.getExpressionId -> fun.getCall
    executionItem match {
      case ExecutionItem.Method(file, cons, function) =>
        ctx.executionService.execute(
          file,
          cons,
          function,
          ctx.cache,
          valsCallback,
          callablesCallback
        )
      case ExecutionItem.CallData(callData) =>
        ctx.executionService.execute(
          callData,
          ctx.cache,
          valsCallback,
          callablesCallback
        )
    }

    callStack match {
      case Nil => ()
      case item :: tail =>
        enterables.get(item) match {
          case Some(call) =>
            runEnso(ExecutionItem.CallData(call), tail, valueCallback)
          case None =>
            ()
        }
    }
  }

  def runEnso(
    contextId: Api.ContextId,
    stack: List[Api.StackItem]
  )(implicit ctx: RuntimeContext): Either[String, Unit] = {
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
    for {
      stackItem <- Either.fromOption(explicitCalls.headOption, "stack is empty")
      item = toExecutionItem(stackItem)
      _ <- Either
        .catchNonFatal(
          runEnso(item, localCalls, onExpressionValueComputed(contextId, _))
        )
        .leftMap { ex =>
          ctx.executionService.getLogger.log(
            Level.FINE,
            s"Error executing a function '${item.function}'",
            ex
          )
          s"error in function: ${item.function}"
        }
    } yield ()
  }

  private def onExpressionValueComputed(
    @unused contextId: Api.ContextId,
    @unused value: ExpressionValue
  ): Unit = {}

  private def toExecutionItem(
    call: Api.StackItem.ExplicitCall
  ): ExecutionItem.Method =
    ExecutionItem.Method(
      call.methodPointer.file,
      call.methodPointer.definedOnType,
      call.methodPointer.name
    )

}

object EnsoExecutionSupport {

  sealed private trait ExecutionItem

  private object ExecutionItem {

    case class Method(
      file: File,
      constructor: String,
      function: String
    ) extends ExecutionItem

    case class CallData(callData: FunctionCall) extends ExecutionItem

  }

}
