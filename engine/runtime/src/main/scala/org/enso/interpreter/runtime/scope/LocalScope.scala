package org.enso.interpreter.runtime.scope

import com.oracle.truffle.api.frame.{FrameDescriptor, FrameSlot, FrameSlotKind}
import org.enso.compiler.pass.analyse.AliasAnalysis
import org.enso.compiler.pass.analyse.AliasAnalysis.Graph
import org.enso.compiler.pass.analyse.AliasAnalysis.Graph.{Scope => AliasScope}

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
//    println(s"LINK FOR $id: $link")
      val slot = frameSlots.get(link.target)

      slot.map(
        new FramePointer(
          if (flattenToParent) link.scopeCount - 1 else link.scopeCount,
          _
        )
      )
    }
  }
}
