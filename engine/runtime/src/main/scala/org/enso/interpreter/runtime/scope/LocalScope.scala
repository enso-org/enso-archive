package org.enso.interpreter.runtime.scope

import java.util

import com.oracle.truffle.api.frame.{FrameDescriptor, FrameSlot, FrameSlotKind}
import org.enso.compiler.pass.analyse.AliasAnalysis
import org.enso.compiler.pass.analyse.AliasAnalysis.Graph
import org.enso.compiler.pass.analyse.AliasAnalysis.Graph.{
  Occurrence,
  Scope => AliasScope
}
import scala.jdk.CollectionConverters._

import scala.collection.mutable

// TODO [AA] Doc this
class LocalScope(
  val aliasingGraph: AliasAnalysis.Graph,
  val scope: AliasAnalysis.Graph.Scope,
  val flattenToParent: Boolean                     = false,
  val frameSlots: mutable.Map[Graph.Id, FrameSlot] = mutable.Map()
) {
  val frameDescriptor: FrameDescriptor = new FrameDescriptor()

  val stateFrameSlot: FrameSlot = frameDescriptor.findOrAddFrameSlot(
    "<<monadic_state>>",
    FrameSlotKind.Object
  )

  def createChild(): LocalScope = createChild(scope.addChild())

  def createChild(
    childScope: AliasScope,
    flattenToParent: Boolean = false
  ): LocalScope = {
    new LocalScope(aliasingGraph, childScope, flattenToParent, frameSlots)
  }

  def createVarSlot(id: Graph.Id): FrameSlot = {
    val slot = frameDescriptor.addFrameSlot(aliasingGraph.idToSymbol(id))
    frameSlots(id) = slot
    slot
  }

  def getFramePointer(id: Graph.Id): Option[FramePointer] = {
    aliasingGraph.defLinkFor(id).flatMap { link =>
      val slot = frameSlots.get(link.target)
      slot.map(
        new FramePointer(
          if (flattenToParent) link.scopeCount - 1 else link.scopeCount,
          _
        )
      )
    }
  }

  def flatten: java.util.Map[String, FramePointer] = flattenWithLevel(0).asJava

  private def flattenWithLevel(
    level: Int
  ): Map[Graph.Symbol, FramePointer] = {
    var parentResult: Map[Graph.Symbol, FramePointer] = scope.parent match {
      case Some(parent) =>
        new LocalScope(aliasingGraph, parent, frameSlots = frameSlots)
          .flattenWithLevel(level + 1)
      case _ => Map()
    }
    scope.occurrences.foreach {
      case x: Occurrence.Def =>
        parentResult += x.symbol -> new FramePointer(
          level,
          frameSlots(x.id)
        )
      case _ =>
    }
    parentResult
  }
}

object LocalScope {
  def root(): LocalScope = {
    val graph = new AliasAnalysis.Graph
    new LocalScope(graph, graph.rootScope)
  }
}
